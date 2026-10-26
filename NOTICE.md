Developer: Kavindu Kavishka

# Third-party software and attribution

VEXO Music is an independently authored application distributed under GNU GPL version 3 or, at your option, any later version. See LICENSE.

- NewPipe Extractor v0.26.5 — Copyright the NewPipe contributors. GPL-3.0-or-later. Source: https://github.com/TeamNewPipe/NewPipeExtractor/tree/v0.26.5
- AndroidX Activity, RecyclerView, Core and Media3 — Copyright the Android Open Source Project. Apache-2.0. https://android.googlesource.com/platform/frameworks/support/ and https://github.com/androidx/media
- OkHttp — Copyright Square, Inc. Apache-2.0. https://github.com/square/okhttp
- Guava — Copyright Google, Inc. Apache-2.0. https://github.com/google/guava
- Desugar JDK libraries — Copyright the Android Open Source Project / OpenJDK contributors; see upstream licence files. https://github.com/google/desugar_jdk_libs
- NewPipe transitive dependencies retain their upstream licences and attribution. The source and binary dependencies are resolved by the pinned Gradle dependency declarations.

Build/test tools: Android Gradle Plugin, Gradle, JUnit, Robolectric and AndroidX Test. They are not part of the app’s feature implementation.

No YMusic application source, logo or name has been copied into this app. The visual system, icons, orbit artwork and interface are original code. YMusic was used as a product reference requested by the user.

Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0
NewPipe Extractor licence: https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/LICENSE

# Music catalogue research (0.17)

The YouTube Music radio adapter was informed by the public queue/parsing architecture in
[Metrolist](https://github.com/MetrolistGroup/Metrolist) (GPL-3.0) and the music transport
in NewPipeExtractor (GPL-3.0). VEXO retains its existing GPL source distribution.
See `docs/MUSIC-RADIO-0.17.md` for the scope and implementation references.

# Search/artwork research (0.19)

- The public music catalogue adapter is informed by MetrolistGroup/Metrolist (GPL-3.0), revision 876e355181fd3b04e7063b10442fef1b721d53b3. See docs/MUSIC-SEARCH-0.19.md.
- Glide 5.0.9 and OkHttp integration — Copyright the Glide contributors, BSD-style licence; includes separately licensed components. Source and licence: https://github.com/bumptech/glide/tree/v5.0.9 and https://github.com/bumptech/glide/blob/v5.0.9/LICENSE
