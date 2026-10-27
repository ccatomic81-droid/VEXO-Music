package space.vexo.music;

import android.content.Context;
import android.telephony.TelephonyManager;
import java.util.*;

public final class RegionPolicy {
  public static final String GLOBAL = "GLOBAL";

  public static boolean global(String code) {
    return GLOBAL.equalsIgnoreCase(code);
  }

  /** Empty means that the provider request must omit the selected country. */
  public static String providerCountry(String code) {
    return global(code) ? "" : valid(code) ? code.toUpperCase(Locale.ROOT) : "LK";
  }

  public static boolean valid(String code) {
    return global(code)
        || code != null
            && Arrays.asList(Locale.getISOCountries()).contains(code.toUpperCase(Locale.ROOT));
  }

  public static String choose(String manual, String network, String sim, String locale) {
    for (String value : new String[] {manual, network, sim, locale, "LK"})
      if (valid(value)) return value.toUpperCase(Locale.ROOT);
    return "LK";
  }

  public static String current(Context context) {
    String manual = context.getSharedPreferences("settings", 0).getString("region", "");
    String network = "", sim = "";
    try {
      TelephonyManager phone =
          (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
      if (phone != null) {
        network = phone.getNetworkCountryIso();
        sim = phone.getSimCountryIso();
      }
    } catch (RuntimeException ignored) {
    }
    return choose(manual, network, sim, Locale.getDefault().getCountry());
  }

  public static String name(String code) {
    if (global(code)) return "Global";
    return new Locale("", code).getDisplayCountry(Locale.ENGLISH);
  }
}
