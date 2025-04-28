package net.inetalliance.potion.obj;

import com.ameriglide.phenix.core.Enums;
import net.inetalliance.potion.annotations.Persistent;
import net.inetalliance.potion.query.Query;
import net.inetalliance.util.security.Principal;
import net.inetalliance.util.security.Ticket;
import net.inetalliance.util.security.auth.Authorized;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

@Persistent
public abstract class AuthorizedPo
    implements Authorized {

  private final transient Set<String> roles;
  private Boolean mustChangePassword;
  private String name;
  private String token;
  private String phone;
  private String firstName;
  private String lastName;
  private String email;

  protected AuthorizedPo() {
    this.roles = new TreeSet<>();
  }

  public static <T extends AuthorizedPo> Query<T> withLastName(final Class<T> type,
      final String lastName) {
    return Query.eq(type, "lastName", lastName);
  }

  public static <T extends AuthorizedPo> Query<T> withFirstName(final Class<T> type,
      final String firstName) {
    return Query.eq(type, "firstName", firstName);
  }

  @Override
  public String getEmail() {
    return email;
  }

  @Override
  public String getPhone() {
    return phone;
  }

  @Override
  public String getFirstName() {
    return firstName;
  }

  @Override
  public String getLastName() {
    return lastName;
  }

  @Override
  public final String getName() {
    return name;
  }

  public <R extends Enum<R>> boolean isAuthorized(final R role) {
    return isAuthorized(Enums.camel(role));
  }

  @Override
  public boolean isAuthorized(final String role) {
    return roles.contains(role);
  }

  @Override
  public Collection<String> getRoles() {
    return roles;
  }

  @Override
  public boolean mustChangePassword() {
    return mustChangePassword;
  }

  @Override
  public String getToken() {
    return token;
  }

  @Override
  public void setMustChangePassword(final boolean mustChangePassword) {
    this.mustChangePassword = mustChangePassword;
  }

  @Override
  public void bind(final Ticket ticket) {
    this.token = ticket.getToken();
    final Principal principal = ticket.getPrincipal();
    this.name = principal.getName();
    this.firstName = principal.getFirstName();
    this.lastName = principal.getLastName();
    this.email = principal.getEmail();
    this.phone = principal.getPhone();
    unbind();
    this.roles.addAll(ticket.getPermissions());
  }

  @Override
  public void unbind() {
    this.roles.clear();
  }
}
