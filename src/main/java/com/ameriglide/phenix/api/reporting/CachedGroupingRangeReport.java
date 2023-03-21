package com.ameriglide.phenix.api.reporting;

import com.ameriglide.phenix.Auth;
import com.ameriglide.phenix.api.ProgressHandler;
import com.ameriglide.phenix.common.Agent;
import com.ameriglide.phenix.common.Source;
import com.ameriglide.phenix.common.Team;
import com.ameriglide.phenix.core.Enums;
import com.ameriglide.phenix.core.Iterators;
import com.ameriglide.phenix.core.Log;
import com.ameriglide.phenix.core.Optionals;
import com.ameriglide.phenix.servlet.PhenixServlet;
import com.ameriglide.phenix.servlet.exception.BadRequestException;
import com.ameriglide.phenix.servlet.exception.ForbiddenException;
import com.ameriglide.phenix.util.ProgressMeter;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.inetalliance.potion.Locator;
import net.inetalliance.potion.cache.RedisJsonCache;
import net.inetalliance.potion.query.Query;
import net.inetalliance.sql.DateTimeInterval;
import net.inetalliance.types.json.JsonMap;

import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.ameriglide.phenix.core.Strings.isEmpty;
import static java.lang.String.join;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static net.inetalliance.types.www.ContentType.JSON;

public abstract class CachedGroupingRangeReport<R, G> extends PhenixServlet {

    private static final Log log = new Log();
    private final String groupParam;
    private final String[] extraParams;
    private RedisJsonCache cache;

    CachedGroupingRangeReport(final String groupParam, final String... extraParams) {
        this.groupParam = groupParam;
        this.extraParams = extraParams;
    }

    static String getSingleExtra(final Map<String, String[]> extras, final String extra, final String defaultValue) {
        final String[] values = extras.get(extra);
        return values==null || values.length==0 ? defaultValue:values[0];
    }

    protected abstract String getGroupLabel(final G group);

    protected abstract String getId(final R row);

    protected abstract Query<R> allRows(final Set<G> groups, final Agent loggedIn, final LocalDateTime intervalStart);

    @Override
    public final void get(final HttpServletRequest request, final HttpServletResponse response) throws Exception {
        var loggedIn = Auth.getAgent(request);
        if (!loggedIn.isSuperUser()) {
            log.error(() -> "%s does not have access to reports".formatted(loggedIn.getName()));
            throw new ForbiddenException("You do not have access to reports");
        }

        final DateTimeInterval interval = getReportingInterval(request);
        final LocalDate start = interval.start().toLocalDate();
        final LocalDate end = interval.start().toLocalDate();
        final String[] mode = request.getParameterValues("mode");
        final String[] contactTypesParam = request.getParameterValues("contactTypes");
        String[] groupParams = request.getParameterValues(groupParam);
        if (groupParams==null) {
            groupParams = new String[]{};
        }
        final String[] teamParam = request.getParameterValues("team");
        final Collection<Team> teams = Optionals
                .of(teamParam)
                .map(array -> Arrays
                        .stream(array)
                        .map(key -> Locator.$(new Team(Integer.parseInt(key))))
                        .flatMap(callCenter -> Iterators.stream(callCenter.toBreadthFirstIterator()))
                        .collect(toList()))
                .orElse(emptyList());

        final StringBuilder extra = new StringBuilder();
        for (String extraParam : extraParams) {
            final String[] extraValues = request.getParameterValues(extraParam);
            if (extraValues!=null) {
                extra
                        .append(",")
                        .append(Arrays.stream(extraValues).map(v -> extraParam + ":" + v).collect(joining(",")));
            }
        }

        final String q = "report:%s,user:%d,start:%s,end:%s,%s:%s,mode:%s,contactTypes:%s,teams:%s%s".formatted(
                getClass().getSimpleName(), (loggedIn.isSuperUser() ? Agent.SYSTEM.get():loggedIn).id,
                american.format(start), american.format(end), groupParam, join(",", groupParams),
                mode==null ? "":join(",", mode), contactTypesParam==null ? "":join(",", contactTypesParam),
                teamParam==null ? "":join(",", teamParam), extra.toString());
        final Map<String, String[]> extras = new HashMap<>(extraParams.length);
        for (final String extraParam : extraParams) {
            extras.put(extraParam, request.getParameterValues(extraParam));
        }

        final String cached = cache.get(q);
        if (isEmpty(cached)) {
            if (request.getParameter("cacheCheck")!=null) {
                log.debug(() -> "Returning empty cache result for %s".formatted((q)));
                respond(response, JsonMap.singletonMap("cached", false));
            } else {
                final Set<G> groups;
                if (groupParams.length > 0) {
                    groups = new LinkedHashSet<>(groupParams.length);
                    for (final String abbreviation : groupParams) {
                        if (!groups.add(getGroup(groupParams, abbreviation))) {
                            throw new BadRequestException("could not find %s for %s", groupParam, abbreviation);
                        }
                    }
                } else {
                    groups = emptySet();
                }

                final EnumSet<Source> sources = mode==null || mode.length==0 || (mode.length==1 && "all".equals(
                        mode[0])) ? EnumSet.noneOf(Source.class):Arrays
                        .stream(mode)
                        .map(s -> Enums.decamel(Source.class, s))
                        .collect(Collectors.toCollection(() -> EnumSet.noneOf(Source.class)));
                ProgressHandler.$.start(loggedIn.id, response, getJobSize(loggedIn, groups, interval), meter -> {
                    final JsonMap map = generate(sources, loggedIn, meter, interval, groups, teams, extras);
                    if (end.plusDays(2).isAfter(LocalDate.now())) {
                        log.debug(() -> "Not caching report %s because end is after midnight today".formatted(q));
                    } else {
                        cache.set(q, map);
                    }
                    return map;
                });
            }
        } else {
            log.debug(() -> "Returning cached report result for %s".formatted(q));
            response.setContentType(JSON.toString());
            try (final PrintWriter writer = response.getWriter()) {
                writer.write(cached);
                writer.flush();
            }
        }
    }

    protected abstract G getGroup(final String[] params, String g);

    protected abstract int getJobSize(final Agent loggedIn, final Set<G> groups, final DateTimeInterval interval);

    protected abstract JsonMap generate(final Set<Source> sources, final Agent loggedIn, final ProgressMeter meter,
                                        final DateTimeInterval intervval, final Set<G> groups,
                                        Collection<Team> callCenters, final Map<String, String[]> extras);

    @Override
    public void init(final ServletConfig config) throws ServletException {
        super.init(config);
        cache = new RedisJsonCache(getClass().getSimpleName());
    }
}
