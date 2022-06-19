package com.ameriglide.phenix.api;

import com.ameriglide.phenix.Auth;
import com.ameriglide.phenix.common.Note;
import com.ameriglide.phenix.common.Opportunity;
import com.ameriglide.phenix.exception.NotFoundException;
import com.ameriglide.phenix.model.Key;
import com.ameriglide.phenix.model.ListableModel;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import net.inetalliance.funky.StringFun;
import net.inetalliance.potion.Locator;
import net.inetalliance.potion.query.Query;
import net.inetalliance.types.json.Json;
import net.inetalliance.types.json.JsonMap;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@WebServlet("/api/note/*")
public class NoteModel extends ListableModel<Note> {
  public NoteModel() {
    super(Note.class, Pattern.compile("^/api/note/(\\d*)/?(.*)?$"));
  }

  @Override
  protected Key<Note> getKey(Matcher m) {
    return Key.$(type, StringFun.utf8UrlDecode(m.group(2)));
  }

  @Override
  protected void setDefaults(Note note, HttpServletRequest request, JsonMap data) {
    super.setDefaults(note, request, data);
    note.setAuthor(Auth.getAgent(request));
    note.setOpportunity(getOpportunity(request));
    note.setCreated(LocalDateTime.now());
  }

  @Override
  protected Json toJson(Key<Note> key, Note note, HttpServletRequest request) {
    var author = note.getAuthor();
    return new JsonMap()
      .$("id",note.id)
      .$("author", author == null ? "Unknown" : author.getFullName())
      .$("created", note.getCreated())
      .$("note", note.getNote());
  }

  protected Opportunity getOpportunity(final HttpServletRequest request) {
    var m = pattern.matcher(request.getRequestURI());
    if(m.matches()) {
      var o = Locator.$(new Opportunity(Integer.valueOf(m.group(1))));
      if (o == null) {
        throw new NotFoundException();
      }
      return o;
    } else {
      throw new IllegalArgumentException();
    }
  }

  @Override
  public Query<Note> all(Class<Note> type, HttpServletRequest request) {
    return Note.withOpportunity(getOpportunity(request));
  }
}
