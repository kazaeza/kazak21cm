package com.kazaeza.muzykalka.music

/**
 * The «хиты 60–80-х» shelf of the songbook (O-18, ADR-032): the harmony of 97 records that were on top of the
 * US charts, in degrees only (rule 1, rule 7) — no lyrics, no melody. The chords come from the expert annotations
 * of the McGill Billboard Project (CC0, bar-by-bar chords with the tonic written down); a section is the loop
 * that the record plays, at most 8 bars (a 12-bar form keeps its 12), slash basses are dropped and colour tones
 * are kept only when they are the harmony. The comment above each piece is the record's year and the id of the
 * annotation in the McGill index, so a doubtful version can be checked against the source.
 *
 * The pieces are listed by year; the library screen sorts them by difficulty (stable).
 */
internal object HitsShelf {
    val ENTRIES: List<BookEntry> = listOf(
        // 1958 · McGill 322
        bookEntry(
            "bird-dog", BookGenre.HITS, 1, "Bird Dog", "The Everly Brothers", Mode.MAJOR, "AABAB",
            "рок-н-ролл на трёх аккордах I, IV и V — блюзовая схема, только короче",
            """
            A: | I | I | I | I | IV | IV | I | I |
            B: | IV7 | IV7 | I | I | V | IV7 | I | I7 |
            """,
        ),
        // 1959 · McGill 157
        bookEntry(
            "sleep-walk", BookGenre.HITS, 2, "Sleep Walk", "Santo & Johnny", Mode.MAJOR, "AABA",
            "I–vi–iv–V: ход 50-х, только четвёртая минорная — горько-сладкий поворот",
            """
            A: | I | vi | iv | V |
            B: | IV | IV | iv | iv | I | I | I7 | I7 |
            """,
        ),
        // 1960 · McGill 250
        bookEntry(
            "handy-man", BookGenre.HITS, 1, "Handy Man", "Jimmy Jones", Mode.MAJOR, "AABABA",
            "I–vi–IV–V: по два такта на I и vi, по одному на IV и V, и V⁷ перед повтором",
            """
            A: | I | I | vi | vi | IV | V | I | V7 |
            B: | IV | IV | I | I | IV | IV | V | V |
            """,
        ),
        // 1960 · McGill 1024
        bookEntry(
            "baby-what-you-want-me-to-do", BookGenre.HITS, 1, "Baby, What You Want Me to Do", "Jimmy Reed", Mode.MAJOR, "AA",
            "двенадцатитактовый блюз: I⁷–IV⁷–I⁷–V⁷, с подходом V⁷ в конце",
            """
            A: | I7 | I7 | I7 | I7 | IV7 | IV7 | I7 | I7 | V7 | V7 | I7 | I7 V7 |
            """,
        ),
        // 1961 · McGill 1239
        bookEntry(
            "will-you-love-me-tomorrow", BookGenre.HITS, 2, "Will You Love Me Tomorrow", "The Shirelles", Mode.MAJOR, "ABABCABA",
            "мажорная III перед vi (III–vi–IV–V) в части B и качели IV–iii в части C",
            """
            A: | I | I | IV | V | I | I | V7 | V7 |
            B: | III | III | vi | vi | IV | V | I | I |
            C: | IV | IV | iii | iii | IV | IV | I | I |
            """,
        ),
        // 1961 · McGill 571
        bookEntry(
            "quarter-to-three", BookGenre.HITS, 1, "Quarter to Three", "Gary U.S. Bonds", Mode.MAJOR, "AABAABAA",
            "I–vi–IV–V в части A, IV–I–IV–V⁷ в части B",
            """
            A: | I | I | vi | vi | IV | IV | V | V |
            B: | IV | IV | I | I | IV | IV | V7 | V7 |
            """,
        ),
        // 1961 · McGill 145
        bookEntry(
            "runaround-sue", BookGenre.HITS, 1, "Runaround Sue", "Dion", Mode.MAJOR, "ABABA",
            "I–vi–IV–V: классический ду-воп, по два такта на аккорд",
            """
            A: | I | I | vi | vi | IV | IV | V |
            B: | IV | IV | I | I | IV | IV | V |
            """,
        ),
        // 1962 · McGill 838
        bookEntry(
            "surfin-safari", BookGenre.HITS, 1, "Surfin' Safari", "The Beach Boys", Mode.MAJOR, "ABABAAB",
            "I–IV–V–I: три аккорда сёрф-рока",
            """
            A: | I | IV | V | I |
            B: | I | I | I | I | IV | IV | I | I |
            """,
        ),
        // 1962 · McGill 66
        bookEntry(
            "do-you-love-me", BookGenre.HITS, 1, "Do You Love Me", "The Contours", Mode.MAJOR, "ABABB",
            "I–IV–V по тактам и минорная iv — горький поворот (IV–iv) перед V",
            """
            A: | I | IV V | I | IV V | I | IV V | IV | iv | V | V7 | V7 | V7 |
            B: | I | IV V | I | IV V | I | IV V | I | V |
            """,
        ),
        // 1963 · McGill 1292
        bookEntry(
            "hes-so-fine", BookGenre.HITS, 1, "He's So Fine", "The Chiffons", Mode.MAJOR, "AABA",
            "ii⁷–V по кругу, а в части B — IV на четыре такта и возврат на I",
            """
            A: | ii7 | V | ii7 | V | ii7 | V | ii7 | V |
            B: | IV | IV | IV | IV | I | I | I | I |
            """,
        ),
        // 1963 · McGill 120
        bookEntry(
            "be-my-baby", BookGenre.HITS, 2, "Be My Baby", "The Ronettes", Mode.MAJOR, "ABCABCAC",
            "III⁷–VI⁷–II⁷–V: цепочка доминант, каждая тянет к следующей",
            """
            A: | I | I | ii | V |
            B: | III7 | III7 | VI7 | VI7 | II7 | II7 | V | V |
            C: | I | I | vi | vi | IV | IV | V7 | V7 |
            """,
        ),
        // 1963 · McGill 270
        bookEntry(
            "in-my-room", BookGenre.HITS, 2, "In My Room", "The Beach Boys", Mode.MAJOR, "ABABCA",
            "♭VII как мягкий шаг в сторону: I–♭VII–I–vi⁷",
            """
            A: | I | I | I bVII | I vi7 |
            B: | ii7 bVII | V | I bVII | I |
            C: | vi7 | V | vi V vi V | I | vi7 | V | ii7 | IVsus4 V |
            """,
        ),
        // 1964 · McGill 302
        bookEntry(
            "do-you-want-to-know-a-secret", BookGenre.HITS, 2, "Do You Want to Know a Secret", "The Beatles", Mode.MAJOR, "ABABCAB",
            "I–iii–♭iii–ii–V⁷: хроматический спуск к доминанте",
            """
            A: | I iii biii | ii V7 | I iii biii | ii V7 | I iii biii | ii V7 | I iii biii | ii V7 |
            B: | vi | IV V |
            C: | IV ii | vi v | IV ii | vi v | ii | V7 |
            """,
        ),
        // 1964 · McGill 50
        bookEntry(
            "love-me-do", BookGenre.HITS, 1, "Love Me Do", "The Beatles", Mode.MAJOR, "AABABA",
            "всего три аккорда: I–IV, а V появляется только в части B",
            """
            A: | I | IV | I | IV | I | IV | IV | IV |
            B: | V | V | IV | I |
            """,
        ),
        // 1964 · McGill 1142
        bookEntry(
            "a-hard-days-night", BookGenre.HITS, 2, "A Hard Day's Night", "The Beatles", Mode.MIXOLYDIAN, "AABAAB",
            "♭VII в мажоре: I–♭VII–I — миксолидийский привкус",
            """
            A: | I IV | I | bVII | I | I IV | I | bVII | I |
            B: | iii | vi | iii | iii | I | vi | IV | V |
            """,
        ),
        // 1964 · McGill 54
        bookEntry(
            "last-kiss", BookGenre.HITS, 1, "Last Kiss", "J. Frank Wilson & The Cavaliers", Mode.MAJOR, "AA",
            "I–vi–IV–V: ход 50-х, по аккорду на такт",
            """
            A: | I | vi | IV | V |
            """,
        ),
        // 1965 · McGill 727
        bookEntry(
            "eight-days-a-week", BookGenre.HITS, 2, "Eight Days a Week", "The Beatles", Mode.MAJOR, "AABABCAB",
            "I–II–IV–I: мажорная II вместо привычной ii",
            """
            A: | I | II | IV | I |
            B: | vi | IV | vi | II | I | II | IV | I |
            C: | V | V | vi | vi | II | II | IV | V |
            """,
        ),
        // 1965 · McGill 587
        bookEntry(
            "unchained-melody", BookGenre.HITS, 2, "Unchained Melody", "The Righteous Brothers", Mode.MAJOR, "ABCAB",
            "I–vi–IV–V в куплете, а в припеве I–V–vi–iii–IV: канонная цепочка",
            """
            A: | I | vi | IV | V | I | vi | V | V |
            B: | I | V | vi | iii | IV | V | I | I7 |
            C: | IV V | IV bIII | IV V | I |
            """,
        ),
        // 1965 · McGill 1125
        bookEntry(
            "treat-her-right", BookGenre.HITS, 1, "Treat Her Right", "Roy Head", Mode.MAJOR, "AA",
            "блюз в 12 тактов на септаккордах: I⁷–IV⁷–I⁷–V⁷–IV⁷–I⁷",
            """
            A: | I7 | I7 | I7 | I7 | IV7 | IV7 | I7 | I7 | V7 | IV7 | I7 | I7 |
            """,
        ),
        // 1965 · McGill 1244
        bookEntry(
            "help", BookGenre.HITS, 2, "Help!", "The Beatles", Mode.MAJOR, "ABABA",
            "I–iii–vi–IV–♭VII: возврат на I через ♭VII — «чёрный ход»",
            """
            A: | I | I | iii | iii | vi | vi | IV bVII | I |
            B: | ii | ii | ii | ii | bVII | bVII | bVII | bVII |
            """,
        ),
        // 1965 · McGill 314
        bookEntry(
            "i-got-you-i-feel-good", BookGenre.HITS, 1, "I Got You (I Feel Good)", "James Brown", Mode.MAJOR, "AABABA",
            "двенадцатитактовый блюз на одних септаккордах — фанк в старой схеме",
            """
            A: | I7 | I7 | I7 | I7 | IV7 | IV7 | I7 | I7 | V7 | IV7 | I7 | I7 |
            B: | IV7 | IV7 | I7 | I7 | IV7 | IV7 | V7 |
            """,
        ),
        // 1966 · McGill 284
        bookEntry(
            "these-boots-are-made-for-walkin", BookGenre.HITS, 1, "These Boots Are Made for Walkin'", "Nancy Sinatra", Mode.MAJOR, "ABABAB",
            "почти всё на I, потом IV, а в части B — ♭III–I",
            """
            A: | I | I | I | I | IV | IV | I | I |
            B: | bIII I |
            """,
        ),
        // 1967 · McGill 1290
        bookEntry(
            "happy-together", BookGenre.HITS, 2, "Happy Together", "The Turtles", Mode.MINOR, "AABABABA",
            "андалузский спуск i–♭VII–♭VI–V⁷, а припев поворачивает в мажор",
            """
            A: | i | i | bVII | bVII | bVI7 | bVI7 | V7 | V7 |
            B: | I | v7 |
            """,
        ),
        // 1967 · McGill 79
        bookEntry(
            "cold-sweat", BookGenre.HITS, 1, "Cold Sweat", "James Brown", Mode.MINOR, "AABAABA",
            "фанк на одном i⁷, а потом ♭VII⁷–♭III⁷ качаются",
            """
            A: | i7 |
            B: | bVII7 | bIII7 | bVII7 | bIII7 | bVII7 | bIII7 | bVII7 | bIII7 |
            """,
        ),
        // 1968 · McGill 946
        bookEntry(
            "i-second-that-emotion", BookGenre.HITS, 1, "I Second That Emotion", "The Miracles", Mode.MAJOR, "ABCABCBC",
            "всего три аккорда — I, IV и V — и ни одного лишнего",
            """
            A: | I | I |
            B: | IV | I |
            C: | I | V IV | IV | I |
            """,
        ),
        // 1968 · McGill 645
        bookEntry(
            "sittin-on-the-dock-of-the-bay", BookGenre.HITS, 2, "(Sittin' On) The Dock of the Bay", "Otis Redding", Mode.MAJOR, "ABABCAB",
            "I–III–IV–II: четыре мажорных аккорда подряд — ни одного минорного в этом обороте",
            """
            A: | I | III | IV | II |
            B: | I | vi | I | vi | I | II | I | VI |
            C: | I V | IV | I V | IV | I V | IV I | bVII | V |
            """,
        ),
        // 1968 · McGill 114
        bookEntry(
            "sunshine-of-your-love", BookGenre.HITS, 1, "Sunshine of Your Love", "Cream", Mode.MAJOR, "ABABAAB",
            "рифф на одном I, дальше V–♭VII–IV",
            """
            A: | I | I | I | I | I | I | I | I |
            B: | V | bVII IV | V | bVII IV | V | bVII IV | V | V |
            """,
        ),
        // 1968 · McGill 841
        bookEntry(
            "born-to-be-wild", BookGenre.HITS, 1, "Born to Be Wild", "Steppenwolf", Mode.MAJOR, "ABABCAB",
            "I–♭III–IV–I: рок-ход с мажорной ♭III",
            """
            A: | I | I7 |
            B: | bIII IV | I |
            C: | I | I | bIII | bIII | IV | bIII | I | I |
            """,
        ),
        // 1968 · McGill 1107
        bookEntry(
            "ob-la-di-ob-la-da", BookGenre.HITS, 2, "Ob-La-Di, Ob-La-Da", "The Beatles", Mode.MAJOR, "ABABCABC",
            "I–V⁷–I–IV в ска-ритме, а в припеве iii–vi",
            """
            A: | I | V7 | V7 | I | I | IV | I V7 | I |
            B: | I | iii vi | I V | I |
            C: | IV | IV | I | I I7 |
            """,
        ),
        // 1969 · McGill 1114
        bookEntry(
            "wichita-lineman", BookGenre.HITS, 2, "Wichita Lineman", "Glen Campbell", Mode.MAJOR, "ABB",
            "Imaj⁷–IV, а в куплете IVmaj⁷–iii⁷ и мажорные II и VI",
            """
            A: | Imaj7 | IV |
            B: | IVmaj7 | iii7 | IVmaj7 | vi7 iii7 | II | VI | VI | V |
            """,
        ),
        // 1969 · McGill 852
        bookEntry(
            "bad-moon-rising", BookGenre.HITS, 1, "Bad Moon Rising", "Creedence Clearwater Revival", Mode.MAJOR, "ABABBAB",
            "I–V–IV–I: три аккорда в духе кантри-рока",
            """
            A: | I | V IV | I | I |
            B: | IV | IV | I | I |
            """,
        ),
        // 1969 · McGill 229
        bookEntry(
            "honky-tonk-women", BookGenre.HITS, 1, "Honky Tonk Women", "The Rolling Stones", Mode.MAJOR, "ABABABB",
            "I–IV и мажорная II перед V (I–II–V)",
            """
            A: | I | I | IV | IV | I | II | V | V |
            B: | I | V | I | I |
            """,
        ),
        // 1969 · McGill 72
        bookEntry(
            "get-together", BookGenre.HITS, 1, "Get Together", "The Youngbloods", Mode.MAJOR, "ABABBABB",
            "I и ♭VII, а в припеве IV–V–I",
            """
            A: | I | I | I | bVII | bVII |
            B: | IV | V | I | IV V | I | I |
            """,
        ),
        // 1969 · McGill 1178
        bookEntry(
            "i-want-you-back", BookGenre.HITS, 2, "I Want You Back", "The Jacksons", Mode.MAJOR, "ABABABCB",
            "I–IV–vi⁷–I–IV–I и каданс ii⁷–V⁷–I: поп-соул на септаккордах",
            """
            A: | I | IV | vi7 I IV I | ii7 V7 I |
            B: | I V vi7 V | IV ii7 V7 | vi7 I IV I | ii7 V7 |
            C: | vi7 iii IV I |
            """,
        ),
        // 1970 · McGill 605
        bookEntry(
            "the-thrill-is-gone", BookGenre.HITS, 1, "The Thrill Is Gone", "B.B. King", Mode.MINOR, "AA",
            "минорный блюз в 12 тактов: i–iv–i–♭VI–V–i",
            """
            A: | i | i | i | i | iv | iv | i | i | bVImaj7 | V7 | i | i |
            """,
        ),
        // 1970 · McGill 364
        bookEntry(
            "evil-ways", BookGenre.HITS, 1, "Evil Ways", "Santana", Mode.DORIAN, "AA",
            "i–IV⁷–V⁷: минор с мажорной IV⁷ — латино-дорийский цвет",
            """
            A: | i IV7 | V7 | V7 |
            """,
        ),
        // 1970 · McGill 560
        bookEntry(
            "cecilia", BookGenre.HITS, 1, "Cecilia", "Simon & Garfunkel", Mode.MAJOR, "ABAA",
            "I–IV–I–IV–I–V: три аккорда, ритм делает остальное",
            """
            A: | I | IV I | IV I | V |
            B: | I | I | I | V I |
            """,
        ),
        // 1970 · McGill 176
        bookEntry(
            "25-or-6-to-4", BookGenre.HITS, 2, "25 or 6 to 4", "Chicago", Mode.MINOR, "AABAABAA",
            "минор: i, потом мажорные ♭VI–♭III–♭VII",
            """
            A: | i | i | i | bVImaj7 V7 |
            B: | bVI | bVI | bIII | bIII | bVII | bVII | bVI | bVI |
            """,
        ),
        // 1970 · McGill 905
        bookEntry(
            "teach-your-children", BookGenre.HITS, 1, "Teach Your Children", "Crosby, Stills & Nash", Mode.MAJOR, "AA",
            "I–IV–I–V: два такта на каждый аккорд",
            """
            A: | I | I | IV | IV | I | I | V | V |
            """,
        ),
        // 1971 · McGill 901
        bookEntry(
            "country-road", BookGenre.HITS, 1, "Country Road", "James Taylor", Mode.MIXOLYDIAN, "ABABCBAB",
            "I–♭VII–IV: миксолидийский ход, а в части C — мажорная ♭III",
            """
            A: | I | I | bVII | bVII IV | I | I | bVII | bVII I |
            B: | I | I | bVII IV | IV | I | I | bVII I | I |
            C: | bIII | bIII | IV I | I | bVII | bVII |
            """,
        ),
        // 1971 · McGill 945
        bookEntry(
            "riders-on-the-storm", BookGenre.HITS, 2, "Riders on the Storm", "The Doors", Mode.MINOR, "ABBABBA",
            "качели i–♭VII и немного iv–v: дорожный минор",
            """
            A: | i | i | i | i | i | i bVII | i bVII | i bVII |
            B: | i bVII | i bVII | i bVII | i | iv v | bVI bVII | i bVII | i bVII |
            """,
        ),
        // 1971 · McGill 1058
        bookEntry(
            "aint-no-sunshine", BookGenre.HITS, 1, "Ain't No Sunshine", "Bill Withers", Mode.MINOR, "AABAA",
            "минорный круг i⁷–v⁷–♭VII⁷ и пауза на i⁷",
            """
            A: | i7 v7 bVII7 | i7 | i7 v7 bVII7 | i7 | v7 | iv7 | i7 v7 bVII7 | i7 |
            B: | i7 v7 bVII7 | i7 |
            """,
        ),
        // 1971 · McGill 819
        bookEntry(
            "mercy-mercy-me-the-ecology", BookGenre.HITS, 2, "Mercy Mercy Me (The Ecology)", "Marvin Gaye", Mode.MAJOR, "AAB",
            "Imaj⁷–vi⁷–ii⁷–V⁷sus⁴: круг септаккордов, а во второй части всё сползает на ♭II",
            """
            A: | Imaj7 | Imaj7 | vi7 | vi7 | ii7 | ii7 | V7sus4 | V7sus4 |
            B: | bIImaj7 | bIImaj7 | bvii7 | bvii7 | biii7 | biii7 | bVI7sus4 | bVI7sus4 |
            """,
        ),
        // 1971 · McGill 248
        bookEntry(
            "id-love-to-change-the-world", BookGenre.HITS, 2, "I'd Love to Change the World", "Ten Years After", Mode.MINOR, "ABABAA",
            "i–♭III–iv–♭VI: минорная петля из четырёх аккордов",
            """
            A: | i | bIII | iv | bVI V7 |
            B: | i | bIII | iv | bVI V |
            """,
        ),
        // 1971 · McGill 752
        bookEntry(
            "levon", BookGenre.HITS, 2, "Levon", "Elton John", Mode.MAJOR, "ABCABCC",
            "ходы по мажорной гамме: I–IV, V–vi, iii–IV–ii⁷",
            """
            A: | I IV | I | IV | I | V | vi | iii IV | ii7 |
            B: | iii vi | IV I | ii7 I | IV I | ii7 | ii7 |
            C: | IV | I |
            """,
        ),
        // 1972 · McGill 128
        bookEntry(
            "brandy-youre-a-fine-girl", BookGenre.HITS, 2, "Brandy (You're a Fine Girl)", "Looking Glass", Mode.MAJOR, "ABABCABB",
            "I–IV–vi–ii–IV в куплете, а в припеве vi–IV по кругу",
            """
            A: | I | IV vi | ii IV | bVII IV | I | IV vi | ii IV | I |
            B: | vi | IV | vi | IV | I | V | IV I | ii V7sus4 |
            C: | vi | vi | IVmaj7 | vi | vi | vi | IVmaj7 | vi |
            """,
        ),
        // 1972 · McGill 1110
        bookEntry(
            "reelin-and-rockin", BookGenre.HITS, 1, "Reelin' & Rockin'", "Chuck Berry", Mode.MAJOR, "AA",
            "12-тактовая сетка на одних септаккордах: I⁷–IV⁷–I⁷–V⁷ — основа рок-н-ролла",
            """
            A: | I7 | I7 | I7 | I7 | IV7 | IV7 | I7 | I7 | V7 | V7 | I7 | I7 V7 |
            """,
        ),
        // 1972 · McGill 720
        bookEntry(
            "rocky-mountain-high", BookGenre.HITS, 1, "Rocky Mountain High", "John Denver", Mode.MAJOR, "ABABA",
            "IV–V–I в припеве и ii⁷–IV–V⁷sus⁴ в куплете",
            """
            A: | I | ii7 IV V7sus4 |
            B: | IV V | I | IV V | I | IV V | I | IV | IV V7sus4 |
            """,
        ),
        // 1973 · McGill 625
        bookEntry(
            "higher-ground", BookGenre.HITS, 2, "Higher Ground", "Stevie Wonder", Mode.MAJOR, "AABAAB",
            "I⁷–♭III–IV: фанк-рифф с мажорной ♭III, потом IV⁷–I⁷",
            """
            A: | I7 bIII IV | I7 | I7 bIII IV | I7 | I7 bIII IV | I7 | I7 bIII IV | I7 |
            B: | IV7 | I7 | IV7 | I7 | IV7 | I7 | II7 | V7 |
            """,
        ),
        // 1973 · McGill 29
        bookEntry(
            "the-joker", BookGenre.HITS, 1, "The Joker", "Steve Miller Band", Mode.MAJOR, "AABAABBA",
            "I–IV–V–IV: три аккорда по кругу",
            """
            A: | I | IV | V | IV |
            B: | I | IV | I | IV | I | IV | V | IV |
            """,
        ),
        // 1973 · McGill 842
        bookEntry(
            "goodbye-yellow-brick-road", BookGenre.HITS, 3, "Goodbye Yellow Brick Road", "Elton John", Mode.MAJOR, "ABCACBCA",
            "доминанты других ступеней (III⁷ и VI⁷) и заём из минора: ♭VI, ♭VII, iv",
            """
            A: | I III7 | IV I | VI7 ii7 | V I V | vi7 III7 | IV | bVI | I V vi |
            B: | ii7 IV V | I IV | bVII V7 | I |
            C: | iv bVII | bIII bVI | iv V7 |
            """,
        ),
        // 1973 · McGill 749
        bookEntry(
            "dyer-maker", BookGenre.HITS, 1, "D'yer Mak'er", "Led Zeppelin", Mode.MAJOR, "ABABA",
            "I–vi–IV–V: ход 50-х в регги-ритме, финал на vi",
            """
            A: | I vi | IV V | I vi | IV V | I vi | IV V | vi | vi |
            B: | vi | V |
            """,
        ),
        // 1974 · McGill 209
        bookEntry(
            "already-gone", BookGenre.HITS, 1, "Already Gone", "Eagles", Mode.MAJOR, "AABABAAB",
            "I–V–IV: три аккорда, IV⁷ перед возвратом",
            """
            A: | I | V | IV | IV7 | I | V | IV | IV |
            B: | I | V | IV | IV |
            """,
        ),
        // 1974 · McGill 221
        bookEntry(
            "feel-like-makin-love", BookGenre.HITS, 2, "Feel Like Makin' Love", "Roberta Flack", Mode.MAJOR, "ABABABAB",
            "ii⁷–V⁷sus⁴–Imaj⁷: мягкий джазовый оборот на септаккордах",
            """
            A: | ii7 | V7sus4 | Imaj7 | bVII7 VI7 |
            B: | IVmaj7 | iii7 | ii7 | vi | IVmaj7 | iii7 | bVII7 | vi |
            """,
        ),
        // 1974 · McGill 182
        bookEntry(
            "annies-song", BookGenre.HITS, 2, "Annie's Song", "John Denver", Mode.MAJOR, "ABB",
            "IV–V–vi–IV–I–iii–vi–V: кружение по ступеням мажора",
            """
            A: | I | Isus4 |
            B: | IV | V | vi | IV | I | iii | vi | V |
            """,
        ),
        // 1975 · McGill 1257
        bookEntry(
            "lovin-you", BookGenre.HITS, 2, "Lovin' You", "Minnie Riperton", Mode.MAJOR, "AABABA",
            "IVmaj⁷–iii⁷–ii⁷–Imaj⁷: септаккорды спускаются ступень за ступенью",
            """
            A: | IVmaj7 iii7 | ii7 Imaj7 |
            B: | ii7 iii7 | ii7 V7sus4 Imaj7 |
            """,
        ),
        // 1975 · McGill 1061
        bookEntry(
            "best-of-my-love", BookGenre.HITS, 1, "Best of My Love", "Eagles", Mode.MAJOR, "AABCBCC",
            "Imaj⁷ и I по очереди, затем V⁷ — тоника в двух красках",
            """
            A: | Imaj7 I | Imaj7 I | V7 | V7 |
            B: | Imaj7 I | Imaj7 I | ii ii7 | ii ii7 | Imaj7 I | Imaj7 I | ii ii7 | ii7 |
            C: | I | I | IV | IV |
            """,
        ),
        // 1975 · McGill 668
        bookEntry(
            "big-yellow-taxi", BookGenre.HITS, 1, "Big Yellow Taxi", "Joni Mitchell", Mode.MAJOR, "ABABABAB",
            "IV–I и IVmaj⁷: плагальные качели",
            """
            A: | IV | I |
            B: | I | IVmaj7 | IVmaj7 | I |
            """,
        ),
        // 1975 · McGill 246
        bookEntry(
            "rhinestone-cowboy", BookGenre.HITS, 1, "Rhinestone Cowboy", "Glen Campbell", Mode.MAJOR, "ABABB",
            "I–IV по кругу, V–I на возврате и Imaj⁷ как украшение тоники",
            """
            A: | I IV | I IV | I IV | I IV | I IV | I IV | V I | I |
            B: | I IV | I | Imaj7 | I | Vsus4 | V IV V | I IV | I |
            """,
        ),
        // 1975 · McGill 1228
        bookEntry(
            "saturday-night", BookGenre.HITS, 2, "Saturday Night", "Bay City Rollers", Mode.MAJOR, "ABCDABCD",
            "I–vi–IV–V в части A, vi–iii–IV–V в части B",
            """
            A: | I | vi | IV | V |
            B: | vi | iii | IV | V | V |
            C: | I | IV | I | V |
            D: | IV | I | ii7 | I | IV | I | ii7 |
            """,
        ),
        // 1976 · McGill 415
        bookEntry(
            "golden-years", BookGenre.HITS, 1, "Golden Years", "David Bowie", Mode.MIXOLYDIAN, "AABAABAA",
            "I–♭VII и ♭VI–iv: два шага вниз от дома",
            """
            A: | I bVII |
            B: | bVI iv |
            """,
        ),
        // 1976 · McGill 824
        bookEntry(
            "50-ways-to-leave-your-lover", BookGenre.HITS, 2, "50 Ways to Leave Your Lover", "Paul Simon", Mode.MINOR, "ABAB",
            "минор: i–v–♭VI–V⁷ и обратно к i",
            """
            A: | i v | bVImaj7 V7 | i V7 | V7 | i v | bVImaj7 V7 | i iv7 | i |
            B: | bIII | bV | bVI | bIII |
            """,
        ),
        // 1976 · McGill 215
        bookEntry(
            "magic-man", BookGenre.HITS, 1, "Magic Man", "Heart", Mode.MIXOLYDIAN, "ABABB",
            "I⁵ и ♭VII, а в припеве ♭III–♭VII–I: рок на квинтаккордах",
            """
            A: | I5 | I5 | bVII | bVII |
            B: | bIII bVII | I5 | bIII bVII | I5 | bVII | I | bIII | IV |
            """,
        ),
        // 1977 · McGill 1171
        bookEntry(
            "night-moves", BookGenre.HITS, 2, "Night Moves", "Bob Seger", Mode.MIXOLYDIAN, "AABABACA",
            "I–♭VII–IV–♭VII в куплете и vi–V–IV–V в припеве",
            """
            A: | I | I bVII | IV | IV bVII |
            B: | vi V | IV V |
            C: | vi | vi | I I7 | I I7 | IV | IV | I | I |
            """,
        ),
        // 1977 · McGill 485
        bookEntry(
            "rich-girl", BookGenre.HITS, 2, "Rich Girl", "Daryl Hall & John Oates", Mode.MAJOR, "ABABA",
            "I–iii⁷–vi, возврат на I после каждого шага и каданс ii⁷–V⁷",
            """
            A: | I iii7 | vi I | IV I | ii7 V7 |
            B: | IVmaj7 | ii7 | I | ii7 iii7 vi | IVmaj7 | ii7 | ii7 | V7 |
            """,
        ),
        // 1978 · McGill 1040
        bookEntry(
            "lay-down-sally", BookGenre.HITS, 1, "Lay Down Sally", "Eric Clapton", Mode.MAJOR, "ABABABB",
            "блюзовый I⁷–IV в куплете, I–IV–V в припеве",
            """
            A: | I7 | I7 | I7 | I7 | IV | IV | I7 | I7 |
            B: | I | I | IV | IV | V | V | I | I |
            """,
        ),
        // 1978 · McGill 958
        bookEntry(
            "miss-you", BookGenre.HITS, 2, "Miss You", "The Rolling Stones", Mode.MINOR, "ABAA",
            "i⁷–iv⁷: минорная качалка, а в B — ♭VI–v⁷–iv⁷",
            """
            A: | i7 | i7 | iv7 | iv7 |
            B: | bVI | v7 | iv7 | iv7 | bVI | v7 | iv7 | V7 |
            """,
        ),
        // 1978 · McGill 124
        bookEntry(
            "promises", BookGenre.HITS, 2, "Promises", "Eric Clapton", Mode.MAJOR, "AABACBAC",
            "I–IV–I–V в куплете и IV–iii–V в припеве",
            """
            A: | I | I | IV | IV | I | I | V | I |
            B: | IV | iii | V | V | IV | iii | V | I |
            C: | IV | IV | I | I | ii7 | ii7 | I | I |
            """,
        ),
        // 1979 · McGill 804
        bookEntry(
            "september", BookGenre.HITS, 2, "September", "Earth, Wind & Fire", Mode.MAJOR, "AABABB",
            "Imaj⁷–vii⁷–vi⁷–vii⁷–iii⁷: цепочка септаккордов, а V⁷ ждёт в конце",
            """
            A: | Imaj7 vii7 vi7 | vii7 iii7 | Imaj7 vii7 vi7 | vii7 iii7 | Imaj7 vii7 vi7 | VII7 iii7 | V7 | V7 |
            B: | vi7 II7 | vii7 iii7 | vi7 II7 | Vmaj7 iii7 | vi7 II7 | vii7 iii7 | V7 | V7 |
            """,
        ),
        // 1979 · McGill 249
        bookEntry(
            "old-time-rock-and-roll", BookGenre.HITS, 1, "Old Time Rock & Roll", "Bob Seger", Mode.MAJOR, "ABAABABB",
            "I–IV–V: три аккорда рок-н-ролла",
            """
            A: | I | I | IV | IV | V | V | I | I |
            B: | I | I | IV | IV | V | V | I | V |
            """,
        ),
        // 1979 · McGill 743
        bookEntry(
            "one-way-or-another", BookGenre.HITS, 2, "One Way or Another", "Blondie", Mode.MAJOR, "AABABA",
            "I и мажорная VI — яркий скачок; в припеве IV–vi–V–iii",
            """
            A: | I | I | I | I | VI | VI | VI | VI |
            B: | IV | vi | V | iii | IV | vi | V | iii |
            """,
        ),
        // 1980 · McGill 307
        bookEntry(
            "cruisin", BookGenre.HITS, 2, "Cruisin'", "Smokey Robinson", Mode.MAJOR, "AABAABAA",
            "I–ii⁷ в куплете и I–IV–♭VII–V⁷ в припеве",
            """
            A: | I | ii7 |
            B: | I | IV | bVII V7 | I |
            """,
        ),
        // 1980 · McGill 1177
        bookEntry(
            "against-the-wind", BookGenre.HITS, 2, "Against the Wind", "Bob Seger", Mode.MAJOR, "ABABAB",
            "I–iii–IV–I–V–IV в куплете и vi–V–I в припеве",
            """
            A: | I | I | iii | iii | IV | I | V | IV |
            B: | vi V | I | vi IV | I | vi V | IV | IV | V |
            """,
        ),
        // 1981 · McGill 898
        bookEntry(
            "i-cant-stand-it", BookGenre.HITS, 1, "I Can't Stand It", "Eric Clapton", Mode.DORIAN, "AABABABA",
            "i–v–IV: дорийский минор с мажорной IV",
            """
            A: | i | v | IV | IV |
            B: | v | bIII IV |
            """,
        ),
        // 1981 · McGill 812
        bookEntry(
            "super-freak", BookGenre.HITS, 1, "Super Freak", "Rick James", Mode.MINOR, "AABAABAA",
            "i–♭VII–i и ♭VI–♭VII–i⁷: рифф минора",
            """
            A: | i | bVII i |
            B: | bVI bVII i7 | bVI bVII i7 | bVI bVII i7 | i | i | i | I |
            """,
        ),
        // 1982 · McGill 92
        bookEntry(
            "shock-the-monkey", BookGenre.HITS, 1, "Shock the Monkey", "Peter Gabriel", Mode.MINOR, "AABABCAA",
            "♭VII на четыре такта, потом i; в C — квинтаккорды I⁵–♭VII⁵",
            """
            A: | i |
            B: | bVII | bVII | bVII | bVII | i | i | i | i |
            C: | I5 | I5 | bVII5 | bVII5 | i | i | bVII5 | bVIIsus4 |
            """,
        ),
        // 1983 · McGill 970
        bookEntry(
            "modern-love", BookGenre.HITS, 2, "Modern Love", "David Bowie", Mode.MAJOR, "AABAAB",
            "I на четыре такта и подъём I–II–iii–IV по ступеням",
            """
            A: | I | I | I | I | V | V | vi7 | vi |
            B: | I | I | II | II | iii | iii | IV | IV |
            """,
        ),
        // 1984 · McGill 334
        bookEntry(
            "somebodys-watching-me", BookGenre.HITS, 1, "Somebody's Watching Me", "Rockwell", Mode.MINOR, "ABAABCBB",
            "i–♭VI–♭VII: тёмная минорная петля",
            """
            A: | i | bVI | i | bVI bVII |
            B: | i | bVI bVII |
            C: | i | i | i | bVI | i | bVImaj7 | i | bVI bVII |
            """,
        ),
        // 1984 · McGill 1279
        bookEntry(
            "let-the-music-play", BookGenre.HITS, 1, "Let the Music Play", "Shannon", Mode.MINOR, "AA",
            "i–♭VI–♭VII: минорная петля танцевального хита",
            """
            A: | i | bVI bVII |
            """,
        ),
        // 1984 · McGill 83
        bookEntry(
            "red-red-wine", BookGenre.HITS, 1, "Red Red Wine", "UB40", Mode.MAJOR, "AABABACA",
            "I–IV–V–IV: регги-петля на трёх аккордах",
            """
            A: | I IV | V IV | I IV | V IV | I IV | V IV | V | IV V |
            B: | V | I | IV | I |
            C: | I IV | V IV | I IV | V IV | V | IV V |
            """,
        ),
        // 1984 · McGill 150
        bookEntry(
            "whats-love-got-to-do-with-it", BookGenre.HITS, 2, "What's Love Got to Do with It", "Tina Turner", Mode.MINOR, "AABCABCD",
            "i⁷–♭VII, а дальше ♭VI–♭VII и ♭III: три мажорных аккорда натурального минора",
            """
            A: | i7 | i7 | bVII | bVII |
            B: | bVI bVII | bVII |
            C: | bIII bVII | bVI bVII |
            D: | bIII | IV | bIII | IV | bII | bIII | i7 | IV |
            """,
        ),
        // 1984 · McGill 542
        bookEntry(
            "wake-me-up-before-you-go-go", BookGenre.HITS, 2, "Wake Me Up Before You Go-Go", "Wham!", Mode.MAJOR, "ABAABAAB",
            "I–ii–I в части A, ii⁷–iii⁷–IV–V в части B",
            """
            A: | I | I | ii | I |
            B: | ii7 | iii7 | IV | iii7 | ii7 | iii7 | IV | V |
            """,
        ),
        // 1984 · McGill 309
        bookEntry(
            "all-through-the-night", BookGenre.HITS, 2, "All Through the Night", "Cyndi Lauper", Mode.MAJOR, "ABCABCAB",
            "I–V в части A, vi–iii–IV–ii в части B и Vsus⁴ перед возвратом в части C",
            """
            A: | I | V | V | V I |
            B: | vi | vi | iii | iii | IV | ii |
            C: | I | vi | IV | ii Vsus4 |
            """,
        ),
        // 1985 · McGill 426
        bookEntry(
            "im-goin-down", BookGenre.HITS, 1, "I'm Goin' Down", "Bruce Springsteen", Mode.MAJOR, "AA",
            "I–V–vi–IV по такту на аккорд — половина поп-музыки",
            """
            A: | I | V | vi | IV |
            """,
        ),
        // 1985 · McGill 1066
        bookEntry(
            "my-hometown", BookGenre.HITS, 1, "My Hometown", "Bruce Springsteen", Mode.MAJOR, "AABAA",
            "I–IV–I–V в части A, vi–I в части B",
            """
            A: | I | I | IV I | V | V | V | I | IV |
            B: | vi | vi | I | I | vi | vi | I | I |
            """,
        ),
        // 1985 · McGill 23
        bookEntry(
            "and-she-was", BookGenre.HITS, 2, "And She Was", "Talking Heads", Mode.MAJOR, "AABCAABC",
            "I–IV в части A, а в части B прыжки по чужим ♭V, ♭II, ♭VI",
            """
            A: | I IV | I |
            B: | bV bII | bVI bII | bV bVI | bII | bV bII | bVI bII | bV bIII | bVI |
            C: | I IV | bVII IV |
            """,
        ),
        // 1985 · McGill 531
        bookEntry(
            "running-up-that-hill", BookGenre.HITS, 1, "Running Up That Hill", "Kate Bush", Mode.MINOR, "ABCABCBC",
            "♭VI–♭VII–i: минорная петля",
            """
            A: | bVI bVII | i i7 |
            B: | iv7 i7 | iv7 |
            C: | bVI | bVII | i | i |
            """,
        ),
        // 1986 · McGill 271
        bookEntry(
            "addicted-to-love", BookGenre.HITS, 1, "Addicted to Love", "Robert Palmer", Mode.MIXOLYDIAN, "AABABAAB",
            "I–♭VII–IV: миксолидийский рок, а в припеве vi⁷–IV–I",
            """
            A: | I | I | bVII | bVII | IV | IV | I | I |
            B: | vi7 | IV | I | I | vi7 | IV | IV | IV |
            """,
        ),
        // 1986 · McGill 729
        bookEntry(
            "pretty-in-pink", BookGenre.HITS, 2, "Pretty in Pink", "The Psychedelic Furs", Mode.MAJOR, "ABABAB",
            "I–ii–V в куплете и ii–iii–IV–iii в припеве",
            """
            A: | I | I | ii | V |
            B: | ii | iii | IV | iii |
            """,
        ),
        // 1986 · McGill 1126
        bookEntry(
            "invisible-touch", BookGenre.HITS, 2, "Invisible Touch", "Genesis", Mode.MAJOR, "ABCABCAD",
            "I–IV–V, затем vi–IVmaj⁷–iii–I и ♭VII⁷ в конце",
            """
            A: | I | IV V |
            B: | vi | IVmaj7 | iii | I |
            C: | II | II | IV | I | IV | I | IV | IV V |
            D: | bVII7 |
            """,
        ),
        // 1986 · McGill 941
        bookEntry(
            "like-a-rock", BookGenre.HITS, 2, "Like a Rock", "Bob Seger", Mode.MIXOLYDIAN, "AABAA",
            "I–IV–♭VII–IV в части A и vi–I–IV–ii–V в части B",
            """
            A: | I | I | IV | IV | bVII | IV | I IV | I |
            B: | vi | vi | I | I | IV | IV | ii | V |
            """,
        ),
        // 1986 · McGill 81
        bookEntry(
            "you-can-call-me-al", BookGenre.HITS, 1, "You Can Call Me Al", "Paul Simon", Mode.MAJOR, "ABABABAB",
            "I–V–IV–V в части A, а в части B V превращается в Vsus⁴",
            """
            A: | I V | IV V |
            B: | I Vsus4 | IV Vsus4 |
            """,
        ),
        // 1986 · McGill 803
        bookEntry(
            "true-colors", BookGenre.HITS, 2, "True Colors", "Cyndi Lauper", Mode.MAJOR, "ABCABCAB",
            "vi⁷–V–Imaj⁷–IV: петля от минорной vi⁷, V ведёт на I, а IV уводит от дома",
            """
            A: | vi7 V | Imaj7 IV |
            B: | vi7 V | I | IV | vi V | I ii | I IV | vi V | IV I |
            C: | IV I | V | IV I | IV V | IV I | IV vi | IV I | IV I |
            """,
        ),
        // 1986 · McGill 981
        bookEntry(
            "venus", BookGenre.HITS, 2, "Venus", "Bananarama", Mode.DORIAN, "ABABABAB",
            "i–IV: минор с мажорной IV — дорийский свет",
            """
            A: | i IV | i IV |
            B: | iv7 bVII | iv7 bVII | i IV | i IV | bVI | V7 | i IV | i IV |
            """,
        ),
        // 1987 · McGill 681
        bookEntry(
            "with-or-without-you", BookGenre.HITS, 1, "With or Without You", "U2", Mode.MAJOR, "AABAA",
            "I–V–vi–IV: четыре аккорда, по такту на каждый",
            """
            A: | I | V | vi | IV |
            B: | I | IV | I | IV | I | IV | vi | IV |
            """,
        ),
        // 1988 · McGill 845
        bookEntry(
            "got-my-mind-set-on-you", BookGenre.HITS, 2, "Got My Mind Set on You", "George Harrison", Mode.MAJOR, "ABACABAA",
            "I–IV–V по кругу, а в части A — vi и мажорная III (vi–III)",
            """
            A: | vi III | vi | I V | I |
            B: | I IV | V | I IV | V | I IV | V | I IV | V |
            C: | I | IV | I | IV | I | IV | I | V |
            """,
        ),
        // 1990 · McGill 827
        bookEntry(
            "just-a-friend", BookGenre.HITS, 1, "Just a Friend", "Biz Markie", Mode.MAJOR, "AA",
            "I–IV–ii–V⁷: оборот хип-хопа 90-х",
            """
            A: | I | IV | ii | ii V7 |
            """,
        ),
    )
}
