package space.vexo.music;

import androidx.media3.common.Player;

/** External controllers may operate existing playback, never inject media or VEXO room actions. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
final class ControllerAccess {
  static boolean app(int uid, int appUid) {
    return uid == appUid;
  }

  static Player.Commands playbackCommands() {
    return new Player.Commands.Builder()
        .addAll(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_PREPARE,
            Player.COMMAND_STOP,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_GET_METADATA,
            Player.COMMAND_GET_AUDIO_ATTRIBUTES,
            Player.COMMAND_GET_VOLUME,
            Player.COMMAND_SET_VOLUME)
        .build();
  }
}
