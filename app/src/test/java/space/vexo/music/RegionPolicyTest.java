package space.vexo.music;

import static org.junit.Assert.*;

import org.junit.Test;

public class RegionPolicyTest {
  @Test
  public void globalIsAnExplicitRegionWithoutACountryRestriction() {
    assertEquals("GLOBAL", RegionPolicy.choose("global", "LK", "LK", "LK"));
    assertTrue(RegionPolicy.valid("GLOBAL"));
    assertEquals("Global", RegionPolicy.name("GLOBAL"));
    assertEquals("", RegionPolicy.providerCountry("GLOBAL"));
    assertEquals("IN", RegionPolicy.providerCountry("in"));
    assertEquals("LK", RegionPolicy.providerCountry("invalid"));
  }

  @Test
  public void manualRegionWinsAndPhoneNetworkWinsOverLocale() {
    assertEquals("LK", RegionPolicy.choose("lk", "AU", "IN", "US"));
    assertEquals("AU", RegionPolicy.choose("", "au", "IN", "US"));
  }

  @Test
  public void invalidAndMissingCountriesFallBackWithoutGuessingGps() {
    assertEquals("IN", RegionPolicy.choose("INVALID", "", "in", "US"));
    assertEquals("GB", RegionPolicy.choose("", "", "", "gb"));
    assertEquals("LK", RegionPolicy.choose("", "", "", ""));
  }
}
