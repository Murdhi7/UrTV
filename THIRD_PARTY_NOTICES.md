# Third-party notices

UrTV is an Android TV home screen and screensaver. Parts of it are adapted from other projects under their own licenses; each adapted file
keeps the copyright header it came with, and the licenses are reproduced here as they require.

## talkies (TechPP) and, through it, tv-vibehack (Gathin) and Matinee (Yiğit Can Polat)

Four things in the home screen follow ideas and code of **talkies** (https://github.com/rajupp/talkies, MIT, © 2026 TechPP):

- the **Control Center** (the round pill and its panel: sleep timer, turn off now, free memory, network, speed test, sound output, screensaver,
  settings) is adapted from talkies' Control Center (`Sleep.java`, `SleepService.java` and the Control Center code of `HomeActivity.java`), which is
  itself adapted from tv-vibehack's launcher (https://github.com/samdegan2311/tv-vibehack, branch `pr/control-center`, MIT, © 2026 Gathin);
- the **speed test** (`SpeedTest`) is adapted from talkies' `SpeedTest.java`, which is adapted from Matinee (https://github.com/Jarvis322/matinee,
  `system/SpeedTest.kt`, MIT, © 2026 Yiğit Can Polat);
- the **HDMI-CEC device names and standby state** in «المصادر» and the **Play Store update bell** follow talkies' approach, which follows Matinee's.

What UrTV added or changed is UrTV's own (see LICENSE). The licenses of the three projects follow.

### talkies

```
MIT License

Copyright (c) 2026 TechPP

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

The Outfit font (app/src/main/res/font/outfit.ttf) is under the SIL Open Font License 1.1 (OUTFIT-OFL.txt), and
code adapted from other projects keeps its own MIT notice: see THIRD_PARTY_NOTICES.md.
```

### tv-vibehack

```
MIT License

Copyright (c) 2026 Gathin

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

---

The Outfit typeface in launcher/app/src/main/res/font/outfit.ttf is copyright
The Outfit Project Authors and licensed under the SIL Open Font License 1.1,
see launcher/FONT-LICENSE-OFL.txt.
```

### Matinee

```
MIT License

Copyright (c) 2026 Yiğit Can Polat

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
