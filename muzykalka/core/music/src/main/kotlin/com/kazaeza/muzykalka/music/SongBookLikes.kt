package com.kazaeza.muzykalka.music

/**
 * More pieces for the «мои лайки» shelf (O-11, ADR-033): 39 tracks from the owner's Spotify likes that have a
 * community chord chart in the open Chordonomicon dataset (CC BY-NC 4.0, see docs/licenses). Only the harmony is
 * kept, as degrees (rule 1, rule 7) — no lyrics, no melody. The tonic is not in the dataset: it is the one that fits
 * the chart and the key Spotify reports for the track, and each piece was read through by hand. A chord of the
 * chart is one bar (the chart does not say how long it lasts); a section is the loop the chart repeats, at most
 * 8 bars; slash basses and colour tones are dropped. The comment above each piece is the row id in the dataset.
 *
 * Appended to the hand-made likes in [SongBook.ENTRIES]; «Вслепую» draws from the whole [BookGenre.LIKED] shelf.
 */
internal object LikesShelf {
    val ENTRIES: List<BookEntry> = listOf(
        // Chordonomicon 638223
        bookEntry(
            "peacefall", BookGenre.LIKED, 1, "peacefall", "Purity Ring", Mode.MINOR, "A",
            "i–♭III–♭VII–♭VI: после минорной тоники три мажорных аккорда по кругу",
            """
            A: | i | bIII | bVII | bVI |
            """,
        ),
        // Chordonomicon 26485
        bookEntry(
            "discard", BookGenre.LIKED, 2, "discard", "Lil Ugly Mane", Mode.MAJOR, "AABAB",
            "I–ii–IV–III: мажорная III вместо минорной — резкий поворот; во второй части I–♭VII–IV–V",
            """
            A: | I | ii | IV | III |
            B: | I | bVII | IV | V |
            """,
        ),
        // Chordonomicon 68337
        bookEntry(
            "liars-love", BookGenre.LIKED, 2, "Liar's Love", "Title Fight", Mode.MAJOR, "ABAB",
            "vi–iii–II–I: нисходящая цепочка к тонике; мажорная II вместо минорной — яркий шаг перед I",
            """
            A: | IV | I |
            B: | vi | iii | II | I |
            """,
        ),
        // Chordonomicon 141852
        bookEntry(
            "below-the-clavicle", BookGenre.LIKED, 1, "Below The Clavicle", "Eartheater", Mode.MINOR, "A",
            "♭VI–iv–i: три аккорда сверху вниз, петля замыкается на минорной тонике",
            """
            A: | bVI | iv | i |
            """,
        ),
        // Chordonomicon 31456
        bookEntry(
            "make-you-cry", BookGenre.LIKED, 1, "Make You Cry", "Title Fight", Mode.MINOR, "AABAAB",
            "i–♭III–iv–♭III: минорная тоника, мажорная ♭III и субдоминанта iv — и обратно на ♭III",
            """
            A: | i | bIII | iv | bIII |
            B: | i | bIII | bVI |
            """,
        ),
        // Chordonomicon 207944
        bookEntry(
            "deranged-for-rock-and-roll", BookGenre.LIKED, 1, "Deranged for Rock & Roll", "Chelsea Wolfe", Mode.MINOR, "AABAB",
            "♭VI–iv–i: нисходящий ход в минорную тонику; в куплете остаются только ♭VI и i",
            """
            A: | bVI | i |
            B: | bVI | iv | i |
            """,
        ),
        // Chordonomicon 60184
        bookEntry(
            "the-perfect-girl", BookGenre.LIKED, 1, "The Perfect Girl", "Mareux", Mode.MINOR, "ABA",
            "V–iv–i–♭VI: мажорная V в миноре, затем вниз через iv к тонике и ♭VI",
            """
            A: | V | iv | i | bVI |
            B: | iv | i | bVI | iv | i |
            """,
        ),
        // Chordonomicon 115373
        bookEntry(
            "seven-years-of-letters", BookGenre.LIKED, 1, "Seven Years of Letters", "The Twilight Sad", Mode.MINOR, "AAB",
            "i–♭VI–iv: минорная петля вниз; во второй части ♭VI–iv–♭III–iv",
            """
            A: | i | bVI | iv |
            B: | bVI | iv | bIII | iv |
            """,
        ),
        // Chordonomicon 35599
        bookEntry(
            "go-easy-palermo", BookGenre.LIKED, 2, "Go Easy (Palermo)", "Interpol", Mode.MINOR, "ABAB",
            "i–iv–♭III–v: в миноре и v минорная — доминанта без напряжения",
            """
            A: | i | iv | bIII | v |
            B: | i7 | bIII | bVII |
            """,
        ),
        // Chordonomicon 49116
        bookEntry(
            "i-want-to-be-with-you", BookGenre.LIKED, 1, "I Want to Be with You", "Sadness", Mode.MAJOR, "A",
            "I–ii–iii–V: подъём по ступеням лада и прыжок на доминанту",
            """
            A: | I | ii | iii | V |
            """,
        ),
        // Chordonomicon 30478
        bookEntry(
            "hypernight", BookGenre.LIKED, 1, "Hypernight", "Title Fight", Mode.MAJOR, "ABABB",
            "I–iii–V: ходы по терциям вверх; в припеве вместо V — минорная vii",
            """
            A: | I | iii | V |
            B: | I | iii | vii |
            """,
        ),
        // Chordonomicon 361729
        bookEntry(
            "staying-power", BookGenre.LIKED, 2, "Staying Power", "Emma Ruth Rundle", Mode.MINOR, "ABABB",
            "i–♭III–♭VI: минорная петля припева; в куплете ♭VI–I–iv, и тоника звучит мажорной",
            """
            A: | bVI | I | iv |
            B: | i | bIII | bVI |
            """,
        ),
        // Chordonomicon 228767
        bookEntry(
            "darkhorse", BookGenre.LIKED, 1, "Darkhorse", "Emma Ruth Rundle", Mode.MINOR, "ABABB",
            "i–♭III–♭VII: рок-петля в миноре; в припеве к ней добавляется ♭VI",
            """
            A: | i | bIII | bVII |
            B: | i | bVII | bIII | bVI |
            """,
        ),
        // Chordonomicon 598731
        bookEntry(
            "somebody-someone", BookGenre.LIKED, 2, "Somebody Someone", "Korn", Mode.MAJOR, "ABAB",
            "I–vi–ii: куплет ходит по минорным vi и ii, а припев раскачивается между ними и V",
            """
            A: | I | vi | ii |
            B: | vi | ii | vi | V | vi | IV | V |
            """,
        ),
        // Chordonomicon 30493
        bookEntry(
            "be-a-toy", BookGenre.LIKED, 1, "Be a Toy", "Title Fight", Mode.MAJOR, "ABABB",
            "I–IV–V–iii–IV: классика I–IV–V, а минорная iii ведёт обратно к IV",
            """
            A: | I | IV | V | iii | IV |
            B: | I | V | I | V | iii | IV |
            """,
        ),
        // Chordonomicon 261410
        bookEntry(
            "i-could-give-you-all-that-you-dont-want", BookGenre.LIKED, 1, "I Could Give You All That You Don't Want", "The Twilight Sad", Mode.MINOR, "ABAB",
            "♭III–♭VI: две мажорные ступени качаются над минорной тоникой",
            """
            A: | i | bIII | bVI | bIII | bVI | bIII | bVI | bVII |
            B: | i | bVII | bVI | bVII |
            """,
        ),
        // Chordonomicon 315369
        bookEntry(
            "everything-is-fine", BookGenre.LIKED, 1, "everything is fine", "Teen Suicide", Mode.MAJOR, "A",
            "I–iii: качели между тоникой и минорной iii, в конце уход на IV",
            """
            A: | I | iii | I | iii | I | iii | I | IV |
            """,
        ),
        // Chordonomicon 620012
        bookEntry(
            "their-kindness-is-charade", BookGenre.LIKED, 1, "Their Kindness Is Charade", "Crystal Castles", Mode.MINOR, "A",
            "♭VI–iv–i: петля из трёх тактов — ♭VI, iv и тоника",
            """
            A: | bVI | iv | i |
            """,
        ),
        // Chordonomicon 22200
        bookEntry(
            "tourmaline", BookGenre.LIKED, 2, "Tourmaline", "The Mars Volta", Mode.MINOR, "ABABCB",
            "i–v–♭III: петля с минорной v; в припеве v–♭VII–i приводит на тонику",
            """
            A: | i | v | bIII |
            B: | v | bVII | i |
            C: | iv | bVI | V | i |
            """,
        ),
        // Chordonomicon 159315
        bookEntry(
            "the-wrong-car", BookGenre.LIKED, 2, "The Wrong Car", "The Twilight Sad", Mode.MINOR, "ABABB",
            "i–♭III–♭VI: минор с двумя мажорными опорами; в припеве iv–♭VI–♭VII",
            """
            A: | i | bIII | bVI | i | bIII | iv | i | bVI |
            B: | iv | bVI | bVII | i | bVII | iv | bVI | bVII |
            """,
        ),
        // Chordonomicon 185663
        bookEntry(
            "16-psyche", BookGenre.LIKED, 1, "16 Psyche", "Chelsea Wolfe", Mode.MINOR, "ABABB",
            "I⁵–♭III⁵: два квинтаккорда без терции — ладу решает ♭III",
            """
            A: | I5 | bIII5 |
            B: | I5 | iv | bIII5 | I5 |
            """,
        ),
        // Chordonomicon 434490
        bookEntry(
            "pale-horse", BookGenre.LIKED, 1, "Pale Horse", "mewithoutYou", Mode.MINOR, "A",
            "♭VI–iv–i: три аккорда подряд, и в тонику приходишь последним",
            """
            A: | bVI | iv | i |
            """,
        ),
        // Chordonomicon 624813
        bookEntry(
            "cheerleader-effect", BookGenre.LIKED, 2, "Cheerleader Effect", "Carpenter Brut", Mode.MINOR, "ABAB",
            "♭VII–IV–i: мажорная IV над минорной тоникой — дорийская краска; в припеве ♭VI–iv–i",
            """
            A: | bVII | IV | i | bVII | IV |
            B: | bVI | iv | i | bVII | bVI | iv | i |
            """,
        ),
        // Chordonomicon 319986
        bookEntry(
            "kill-v-maim", BookGenre.LIKED, 1, "Kill V. Maim", "Grimes", Mode.MINOR, "A",
            "i–♭III–♭VI–iv: четыре аккорда по кругу — одна петля на всю песню",
            """
            A: | i | bIII | bVI | iv |
            """,
        ),
        // Chordonomicon 388838
        bookEntry(
            "passing-through", BookGenre.LIKED, 1, "Passing Through", "Cult Of Luna", Mode.MINOR, "A",
            "i–♭III–♭VI: минор с двумя мажорными опорами и ничего лишнего",
            """
            A: | i | bIII | bVI |
            """,
        ),
        // Chordonomicon 211649
        bookEntry(
            "then-again", BookGenre.LIKED, 2, "Then Again", "Half Moon Run", Mode.MAJOR, "ABAB",
            "I–ii⁷–iii–IV: подъём по четырём ступеням подряд",
            """
            A: | I | ii7 | iii | IV |
            B: | vi | III | I | IVmaj7 |
            """,
        ),
        // Chordonomicon 624738
        bookEntry(
            "unmade", BookGenre.LIKED, 1, "Unmade", "Thom Yorke", Mode.MINOR, "A",
            "i⁷–♭VImaj⁷–v⁷: мягкие септаккорды, доминанты нет",
            """
            A: | i7 | bVImaj7 | v7 |
            """,
        ),
        // Chordonomicon 40145
        bookEntry(
            "alter-ego", BookGenre.LIKED, 2, "Alter Ego", "Tame Impala", Mode.MAJOR, "ABAB",
            "ii–V–iii–vi–IV–ii–V–I: почти весь лад по кругу, финал — ii–V–I",
            """
            A: | I | IV |
            B: | ii | V | iii | vi | IV | ii | V | I |
            """,
        ),
        // Chordonomicon 90615
        bookEntry(
            "soshy", BookGenre.LIKED, 1, "soshy", "Purity Ring", Mode.MINOR, "ABABB",
            "♭VII–♭VI–iv: припев идёт вниз по ступеням и ни разу не касается тоники",
            """
            A: | i | bVIIsus2 | bVI | iv |
            B: | bVII | bVI | iv |
            """,
        ),
        // Chordonomicon 614528
        bookEntry(
            "flood-on-the-floor", BookGenre.LIKED, 2, "flood on the floor", "Purity Ring", Mode.MINOR, "ABCABB",
            "♭VI–♭VII–iv: в припеве два мажорных шага вверх и минорная iv, без тоники",
            """
            A: | bVI | bVII | iv |
            B: | i | bVI | bVII | iv |
            C: | i | bVII | v | bVI |
            """,
        ),
        // Chordonomicon 380863
        bookEntry(
            "sea-castle", BookGenre.LIKED, 1, "sea castle", "Purity Ring", Mode.MINOR, "A",
            "iv–i–iv–i–V: качели между iv и i и мажорная V в конце круга",
            """
            A: | iv | i | iv | i | V |
            """,
        ),
        // Chordonomicon 622474
        bookEntry(
            "obedear", BookGenre.LIKED, 1, "obedear", "Purity Ring", Mode.MINOR, "A",
            "i–♭VI–iv–♭VI: одна петля на всю песню",
            """
            A: | i | bVI | iv | bVI |
            """,
        ),
        // Chordonomicon 41162
        bookEntry(
            "pillars", BookGenre.LIKED, 2, "Pillars", "Sunny Day Real Estate", Mode.MINOR, "AAB",
            "i–♭VI–V: минор, ♭VI и мажорная V — доминанта подвешивает петлю",
            """
            A: | i | bVI | V |
            B: | i | bVI | bIII | v | bVI | i | bVI | bVII |
            """,
        ),
        // Chordonomicon 597857
        bookEntry(
            "packt-like-sardines-in-a-crushd-tin-box", BookGenre.LIKED, 1, "Packt Like Sardines In a Crushd Tin Box", "Radiohead", Mode.MAJOR, "ABAB",
            "I–V–IV–V–IV–V: V и IV чередуются, пока цикл не замкнёт ii",
            """
            A: | ii | I | V | IV | V | IV | V |
            B: | I | V | IV | V | IV | V | ii |
            """,
        ),
        // Chordonomicon 183287
        bookEntry(
            "handsome-devil-peel-session", BookGenre.LIKED, 1, "Handsome Devil (Peel Session)", "The Smiths", Mode.MINOR, "A",
            "♭VI–♭VII–i: две мажорные ступени вверх и приход в минорную тонику",
            """
            A: | bVI | bVII | i |
            """,
        ),
        // Chordonomicon 327939
        bookEntry(
            "all-is-now-harmed", BookGenre.LIKED, 1, "All Is Now Harmed", "Ben Howard", Mode.MINOR, "A",
            "i–♭VI–♭III–♭VII: минорная тоника и три мажорных аккорда по кругу",
            """
            A: | i | bVI | bIII | bVII |
            """,
        ),
        // Chordonomicon 27680
        bookEntry(
            "wow-i-hate-this-song", BookGenre.LIKED, 1, "Wow, I Hate This Song", "The Used", Mode.MINOR, "A",
            "i–♭VII–♭VI: спуск от тоники вниз по ладу",
            """
            A: | i | bVII | bVI |
            """,
        ),
        // Chordonomicon 10682
        bookEntry(
            "the-hellfire-club", BookGenre.LIKED, 2, "The Hellfire Club", "Thornhill", Mode.MAJOR, "ABACB",
            "тоника то простая, то с мажорной септимой; в припеве ii–vi–Imaj⁷ — мягкий мажор",
            """
            A: | I | Imaj7 | I | Imaj7 | ii |
            B: | ii | vi | Imaj7 |
            C: | I | IV | V | I |
            """,
        ),
        // Chordonomicon 9485
        bookEntry(
            "heroine", BookGenre.LIKED, 1, "Heroine", "Thornhill", Mode.MINOR, "ABAB",
            "iv–i–V: минорная петля, где V мажорная и тянет обратно к тонике",
            """
            A: | i | bVI | i | bVI | i | iv | V |
            B: | iv | i | V |
            """,
        ),
    )
}
