package space.vexo.music;

import static org.junit.Assert.*;

import org.junit.Test;

public class RegionPolicyTest {
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
