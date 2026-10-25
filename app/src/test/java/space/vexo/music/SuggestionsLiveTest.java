package space.vexo.music;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.*;

public class SuggestionsLiveTest {
  @Test
  public void youtubeSuggestionsReturnRealTerms() throws Exception {
    Assume.assumeTrue("1".equals(System.getenv("VEXO_SUGGESTIONS_LIVE_TEST")));
    List<String> values = SearchSuggestions.ONLINE.fetch("sinhala", "LK");
    assertFalse("YouTube should return autocomplete terms", values.isEmpty());
    assertTrue(values.stream().allMatch(s -> s != null && !s.trim().isEmpty()));
    System.out.println("Received " + values.size() + " live YouTube suggestions");
  }
}
