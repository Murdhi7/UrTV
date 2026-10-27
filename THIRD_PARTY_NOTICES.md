# Third-party notices

Code and ideas taken from other projects, with their licenses. Fonts, photos and lists carry their own notices next to them:
`assets/fonts/*-NOTICE.txt` and `*-OFL.txt`, `docs/PHOTO-CREDITS.md`, `optimize/lists/community/LICENSE-CleanYourTV.txt`, and the
launcher's origin in `android/launcher/LICENSE-tv-home`.

## talkies

The home screen's spring motion (`android/launcher/src/main/java/sa/urtv/home/Spring.java`) and the ideas for the channel and
«أكمل المشاهدة» rows and the automatic light and dark look are from talkies (https://github.com/rajupp/talkies), under this license:

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
```

## Free My TV

The home screen's setup («إعداد تلفزيوني», `android/launcher/src/main/java/sa/urtv/home/setup/AdbClient.java` and `AdbKeys.java`) is adapted from Free My TV's
`app/src/io/freemytv/Adb.java`: the way an app talks to the TV's own adb daemon at 127.0.0.1:5555 (the RSA key, the "Allow USB debugging?" prompt, the
Android 8 to 10 handling of the answer). Both files keep their credit header. The idea of running the setup from the TV itself follows Free My TV's
`HomeMode.java`; the commands and lists are UrTV's own (the ones `optimize/` runs from a computer).

talkies has `Spring.java` from Free My TV (https://github.com/simbasjungles/free-my-tv, `app/src/io/freemytv/Spring.java`), under
this license:

```
MIT License

Copyright (c) 2026 Free My TV contributors

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
