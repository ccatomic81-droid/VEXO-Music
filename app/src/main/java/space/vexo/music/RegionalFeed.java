package space.vexo.music;

import android.content.Context;
import java.util.*;
import org.json.*;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.kiosk.KioskExtractor;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.search.SearchExtractor;

public final class RegionalFeed {
  public final String country, source;
  public final boolean ranked;
  public final long fetchedAt;
  public final List<Track> tracks;

  public RegionalFeed(
      String country, String source, boolean ranked, long fetchedAt, List<Track> tracks) {
    this.country = country;
    this.source = source;
    this.ranked = ranked;
    this.fetchedAt = fetchedAt;
    this.tracks = tracks;
  }

  public static RegionalFeed fetch(String country) throws Exception {
    OnlineMusic.initialize();
    if (RegionPolicy.global(country)) {
      List<Track> songs =
          MixCatalogue.songs(
              OnlineMusic.preview(
                  "worldwide global viral songs international hits", RegionPolicy.GLOBAL));
      if (songs.isEmpty()) throw new java.io.IOException("No worldwide music results");
      return new RegionalFeed(
          RegionPolicy.GLOBAL,
          "YouTube Music · worldwide song picks · not a ranked chart",
          false,
          System.currentTimeMillis(),
          songs);
    }
    if (!RegionPolicy.global(country))
      try {
        KioskExtractor<?> charts =
            ServiceList.YouTube.getKioskList().getExtractorById("trending_music", null);
        if (!RegionPolicy.global(country))
          charts.forceContentCountry(new ContentCountry(RegionPolicy.providerCountry(country)));
        charts.fetchPage();
        List<Track> songs = OnlineMusic.convert(charts.getInitialPage().getItems());
        if (!songs.isEmpty())
          return new RegionalFeed(
              country, "YouTube trending music chart", true, System.currentTimeMillis(), songs);
      } catch (Exception chartUnavailable) {
        /* Some countries, including LK, have no YouTube chart. */
      }
    String query =
        RegionPolicy.global(country)
            ? "worldwide global viral songs international hits"
            : RegionPolicy.name(country) + " viral songs";
    if (country.equals("LK")) query = "Sinhala Tamil Sri Lanka viral songs";
    SearchExtractor search =
        ServiceList.YouTube.getSearchExtractor(
            ServiceList.YouTube.getSearchQHFactory()
                .fromQuery(query, Collections.singletonList("music_songs"), ""));
    if (!RegionPolicy.global(country))
      search.forceContentCountry(new ContentCountry(RegionPolicy.providerCountry(country)));
    search.fetchPage();
    List<Track> songs = OnlineMusic.convert(search.getInitialPage().getItems());
    if (songs.isEmpty()) throw new java.io.IOException("No regional music results");
    return new RegionalFeed(
        country,
        RegionPolicy.global(country)
            ? "YouTube Music · worldwide song picks · not a ranked chart"
            : "YouTube Music · regional song picks · not a ranked chart",
        false,
        System.currentTimeMillis(),
        songs);
  }

  public void persist(Context c) {
    try {
      JSONObject o = new JSONObject();
      o.put("country", country);
      o.put("source", source);
      o.put("ranked", ranked);
      o.put("at", fetchedAt);
      JSONArray a = new JSONArray();
      for (Track t : tracks) a.put(t.json());
      o.put("tracks", a);
      c.getSharedPreferences("regional_feed", 0).edit().putString(country, o.toString()).apply();
    } catch (JSONException ignored) {
    }
  }

  public static RegionalFeed cached(Context c, String country) {
    try {
      JSONObject o =
          new JSONObject(c.getSharedPreferences("regional_feed", 0).getString(country, ""));
      if (!country.equals(o.getString("country"))) return null;
      List<Track> tracks = new ArrayList<>();
      JSONArray a = o.getJSONArray("tracks");
      for (int i = 0; i < a.length(); i++) tracks.add(Track.from(a.getJSONObject(i)));
      return new RegionalFeed(
          country, o.getString("source"), o.getBoolean("ranked"), o.getLong("at"), tracks);
    } catch (Exception e) {
      return null;
    }
  }
}
