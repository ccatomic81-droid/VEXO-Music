package space.vexo.music;

import java.util.*;

/** Search collections, not invented measured audio labels or popularity charts. */
final class MusicCategory {
  final String id, name, group, terms;
  final int color;

  MusicCategory(String id, String name, String group, String terms, int color) {
    this.id = id;
    this.name = name;
    this.group = group;
    this.terms = terms;
    this.color = color;
  }

  String query(String typed, String language) {
    return (typed.trim() + " " + (language.equals("Any") ? "" : language) + " " + terms + " music")
        .trim()
        .replaceAll(" +", " ");
  }

  static final List<MusicCategory> ALL =
      Collections.unmodifiableList(
          Arrays.asList(
              new MusicCategory("pop", "Pop", "Genres", "pop", 0xff36483b),
                  new MusicCategory("hiphop", "Hip-hop", "Genres", "hip hop", 0xff39314b),
              new MusicCategory("rock", "Rock", "Genres", "rock", 0xff4a3434),
                  new MusicCategory("classical", "Classical", "Genres", "classical", 0xff343e4c),
              new MusicCategory("electronic", "Electronic", "Genres", "electronic", 0xff334b4b),
                  new MusicCategory("acoustic", "Acoustic", "Genres", "acoustic", 0xff494333),
              new MusicCategory(
                      "chill", "Chill", "Moods & activities", "chill relaxing", 0xff2e4943),
                  new MusicCategory("love", "Love", "Moods & activities", "love songs", 0xff4c3541),
              new MusicCategory("sad", "Sad", "Moods & activities", "sad songs", 0xff354153),
                  new MusicCategory(
                      "focus",
                      "Focus",
                      "Moods & activities",
                      "instrumental study focus",
                      0xff3d3d4e),
              new MusicCategory(
                      "workout", "Workout", "Moods & activities", "workout energetic", 0xff4a4230),
                  new MusicCategory(
                      "party", "Party", "Moods & activities", "party dance", 0xff4d354d),
              new MusicCategory(
                      "sleep", "Sleep", "Moods & activities", "sleep ambient", 0xff30364c),
                  new MusicCategory(
                      "srilanka", "Sri Lankan", "Regional", "Sri Lankan songs", 0xff3d4c32)));

  static MusicCategory find(String id) {
    for (MusicCategory c : ALL) if (c.id.equals(id)) return c;
    return null;
  }
}
