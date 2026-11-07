package com.kazaeza.muzykalka.music

/** Shelf of the built-in songbook. */
enum class BookGenre(val ru: String) { LIKED("мои лайки"), POP("поп и рок"), BLUES("блюз"), JAZZ("джаз") }

/**
 * One built-in teaching piece: the harmony of a well-known song (or a standard form like the blues)
 * written in degrees. [teaches] says in one line why it is in the book. The user adds a piece to
 * their own library with one tap; from then on it is theirs to edit.
 */
data class BookEntry(
    val id: String,
    val genre: BookGenre,
    /** 1 — two to four chords, 2 — a real song form, 3 — jazz changes. */
    val difficulty: Int,
    val teaches: String,
    val song: Song,
) {
    init {
        require(difficulty in 1..3) { "Difficulty is 1..3" }
    }
}

/**
 * The mini-library (ADR-013): harmony of classic teaching songs and jazz standards, in degrees
 * only (rule 1). Changes are the common lead-sheet versions, simplified to triads and sevenths;
 * passing chords and reharmonisations are left out on purpose — edit after adding if your
 * version differs.
 *
 * The [BookGenre.LIKED] shelf (ADR-018) is the user's own favourites from their Spotify likes:
 * the tonic is checked against the track's key, chords come from popular community charts,
 * slash basses and colour tones are dropped, and only the harmony in degrees is kept.
 */
object SongBook {
    val ENTRIES: List<BookEntry> = listOf(
        // ---------------------------------------------------------------- my likes (ADR-018)
        entry(
            "covet", BookGenre.LIKED, 1, "Covet", "Basement", Mode.MAJOR, "AABAA",
            "I–vi–iii–V по кругу и подход на IV",
            """
            A: | I | vi | iii | V |
            B: | IV | IV |
            """,
        ),
        entry(
            "whole", BookGenre.LIKED, 1, "Whole", "Basement", Mode.MAJOR, "AABAB",
            "мажорная III — яркий шаг вверх вместо минорной iii",
            """
            A: | I | III | I | III |
            B: | IV | III | IV | I |
            """,
        ),
        entry(
            "flume", BookGenre.LIKED, 1, "Flume", "Bon Iver", Mode.MAJOR, "ABCABCAC",
            "I–iii–vi и IV–vi–I: соседние аккорды с общими нотами",
            """
            A: | I | iii | vi | iii |
            B: | I | iii | V | V |
            C: | IV | vi | I | I |
            """,
        ),
        entry(
            "heaviside", BookGenre.LIKED, 2, "Heaviside", "Citizen", Mode.MINOR, "ABCABCA",
            "минор с дорийскими ii и IV рядом с обычной iv",
            """
            A: | i ii | bIII bVII | v | bVII IV |
            B: | i | bVII | i | bVII | iv | iv |
            C: | i | bVII | iv | v |
            """,
        ),
        entry(
            "how-does-it-feel", BookGenre.LIKED, 1, "How Does It Feel?", "Citizen", Mode.MINOR, "AAAAB",
            "♭VI–i–♭VII, а в конце мажорная IV — дорийский свет",
            """
            A: | bVImaj7 | bVIadd9 | i | bVII |
            B: | i | bIII | bVII | IV |
            """,
        ),
        entry(
            "numb-yourself", BookGenre.LIKED, 1, "Numb Yourself", "Citizen", Mode.MINOR, "ABABC",
            "i–♭VI–v: минор с минорной доминантой",
            """
            A: | i | bVI v | i | bVI v |
            B: | bVI | i | bVI | i |
            C: | bVI | bIII | bVI | bIII |
            """,
        ),
        entry(
            "in-blur", BookGenre.LIKED, 2, "In Blur", "Deafheaven", Mode.MINOR, "ABCABC",
            "iv–♭VImaj⁷–♭III–i: шугейзовый минор",
            """
            A: | i | bVImaj7 | bIII | i |
            B: | iv | bVImaj7 | bIII | i | iv | bVImaj7 | i | bVII |
            C: | i | bVImaj7 iv | i | bVImaj7 iv | bVI | i | bVII iv | bVI bVII |
            """,
        ),
        entry(
            "between-the-bars", BookGenre.LIKED, 2, "Between the Bars", "Elliott Smith", Mode.MINOR, "AABAAB",
            "V⁷ из гармонического минора и минорная ♭vi в конце припева",
            """
            A: | i7 | bVI | bIII | bVII | i7 | bVI | bIII V7 | bVI |
            B: | bVI bVII | i | bVI bVII | i | bVI bVII | i bVI | bvi | bvi |
            """,
        ),
        entry(
            "full-circle", BookGenre.LIKED, 2, "Full Circle", "Half Moon Run", Mode.MINOR, "AAABA",
            "♭VImaj⁷–v–iv⁶–i: минор шагами вниз",
            """
            A: | bVImaj7 | v | iv6 | i | i bIIImaj7 | bVII |
            B: | i bIIImaj7 | bVII | i bIIImaj7 | bVII |
            """,
        ),
        entry(
            "bloodhail", BookGenre.LIKED, 1, "Bloodhail", "Have A Nice Life", Mode.MINOR, "AABAC",
            "♭VI–i: два аккорда и вся тяжесть минора",
            """
            A: | bVI | i | bVI | i |
            B: | bVI i | bVII |
            C: | bIII | i | bVI | i |
            """,
        ),
        entry(
            "sea-of-worry", BookGenre.LIKED, 1, "Sea of Worry", "Have A Nice Life", Mode.MAJOR, "AABCC",
            "IV и минорная iv подряд — заимствование из минора",
            """
            A: | iii | I | I | IVmaj7 iv |
            B: | I | vi | I | vi | I | vi | iii | I |
            C: | Imaj7 | vi | Imaj7 | vi | Imaj7 | vi | iii | I |
            """,
        ),
        entry(
            "obstacle-1", BookGenre.LIKED, 1, "Obstacle 1", "Interpol", Mode.LYDIAN, "ABABC",
            "лидийский лад: Vmaj⁷ с #4 вместо обычной доминанты",
            """
            A: | I | iii | I | iii |
            B: | Imaj7 | V | iii | Vmaj7 |
            C: | I | Vmaj7 | I | Vmaj7 |
            """,
        ),
        entry(
            "something-changed", BookGenre.LIKED, 2, "Something Changed", "Interpol", Mode.MAJOR, "ABCDABCD",
            "♭III⁶–♭VI–IV–I: тёмные краски из минора, а дом мажорный",
            """
            A: | bIII6 | bVI | IVsus2 | I |
            B: | bIII6 | bVI | IVsus4 | I I7 |
            C: | bIII6 | bVI | IVsus2 | I I7 |
            D: | bIII6 | bVI | iv | I |
            """,
        ),
        entry(
            "dum-surfer", BookGenre.LIKED, 2, "Dum Surfer", "King Krule", Mode.MAJOR, "A",
            "iiø⁷ и VImaj⁷ — джазовые краски вне лада",
            "A: | Imaj7 iiø7 | Imaj7 iiø7 | Imaj7 iiø7 | VImaj7 IVmaj7 |",
        ),
        entry(
            "my-kind-of-woman", BookGenre.LIKED, 1, "My Kind of Woman", "Mac DeMarco", Mode.MINOR, "AABAAB",
            "♭III–iv–v–♭VI: лестница по натуральному минору",
            """
            A: | bVII bVI | bVII bIII | i | i |
            B: | bIII iv | v bVI | iv | iv |
            """,
        ),
        entry(
            "january-1979", BookGenre.LIKED, 1, "January 1979", "mewithoutYou", Mode.MAJOR, "ABABCA",
            "мажорная II в припеве — лидийский подъём",
            """
            A: | I | iii | I | iii |
            B: | II | iii | II | II |
            C: | I | V | I | Imaj7 | V | iii | V | V |
            """,
        ),
        entry(
            "in-the-afternoon", BookGenre.LIKED, 2, "In the Afternoon", "MGMT", Mode.PHRYGIAN, "ABABB",
            "фригийский лад: ♭II над минорной тоникой",
            """
            A: | i | bvii | i | bvii | i | bII | bIII | bvii |
            B: | bVI | bII | i | bvii | i | bII | bVI | bII |
            """,
        ),
        entry(
            "all-i-need", BookGenre.LIKED, 1, "All I Need", "Radiohead", Mode.MINOR, "ABAB",
            "♭VI–i–♭III: минор, который звучит почти как мажор",
            """
            A: | bVI | i | bIII | bVI |
            B: | bVI | i | bVI | i | bVI | i | bIII | bVI |
            """,
        ),
        entry(
            "daydreaming", BookGenre.LIKED, 2, "Daydreaming", "Radiohead", Mode.MINOR, "AABCAABC",
            "IV и iv рядом: дорийская и обычная шестая ступень",
            """
            A: | i | bVImaj7 | bIII | bVImaj7 |
            B: | i | bVImaj7 | bIII | IVsus2 |
            C: | IVsus2 | iv | IVsus2 | iv |
            """,
        ),
        entry(
            "pyramid-song", BookGenre.LIKED, 2, "Pyramid Song", "Radiohead", Mode.PHRYGIAN, "AABABAB",
            "♭II и ♭III над мажорной тоникой — фригийский вайб",
            """
            A: | I | bIImaj7 | bIII6 | bIImaj7 |
            B: | i | bVIIadd9 | bIImaj7 | bIImaj7 |
            """,
        ),
        entry(
            "reckoner", BookGenre.LIKED, 2, "Reckoner", "Radiohead", Mode.MINOR, "AABCA",
            "мажорная IV в миноре и V рядом с v",
            """
            A: | i bVII | bVI | i | bVI |
            B: | i bVII | bVI i | bVII IV | bVI |
            C: | bVI | i | V | V | bVI | i | V v | i |
            """,
        ),
        entry(
            "less-i-know", BookGenre.LIKED, 2, "The Less I Know the Better", "Tame Impala", Mode.MINOR, "ABCABC",
            "неаполитанская ♭II в припеве",
            """
            A: | i | bVII | bIII | v bVII |
            B: | v | i | bVII | bIII |
            C: | bIII | i | bII | bII | bIII | i | bII | bVII |
            """,
        ),
        entry(
            "a-fragile-thing", BookGenre.LIKED, 1, "A Fragile Thing", "The Cure", Mode.MINOR, "AABCAABC",
            "весь натуральный минор: i–iv–v–♭VI–♭VII",
            """
            A: | i | iv | v | bVI | bVII | iv |
            B: | v | iv | v | bVI | v | iv | bVI | bVII |
            C: | i bVII | i iv | i bVII | i |
            """,
        ),
        entry(
            "never-say-goodbye", BookGenre.LIKED, 1, "I Can Never Say Goodbye", "The Cure", Mode.MINOR, "ABAB",
            "i–♭VII–v–♭VI: минор шагами вниз",
            """
            A: | i | bVII6 | v | bVI | i | bVII6 | v | iv |
            B: | i | bVII6 | v | bVImaj7 | v | bVI | v | iv |
            """,
        ),
        entry(
            "someday", BookGenre.LIKED, 1, "Someday", "The Strokes", Mode.MAJOR, "AABCAB",
            "I–ii–IV и I–V–vi–IV: классика гаражного рока",
            """
            A: | I | ii7 | IV | I |
            B: | I V7 | vi | IV | I |
            C: | V | V |
            """,
        ),
        entry(
            "head-in-the-ceiling-fan", BookGenre.LIKED, 1, "Head in the Ceiling Fan", "Title Fight", Mode.MAJOR, "ABCB",
            "качели I–IV и минорная vii — редкий цвет",
            """
            A: | I | IV | V | I | iii | IV |
            B: | I | IV | I | IV |
            C: | vii | I | iii | IV |
            """,
        ),
        entry(
            "lefty", BookGenre.LIKED, 1, "Lefty", "Title Fight", Mode.MAJOR, "ABCABC",
            "IV–ii–I и мажорная III в припеве",
            """
            A: | IV | ii | I | I |
            B: | IV | iii | I | I |
            C: | IV | ii | III | III |
            """,
        ),
        entry(
            "numb-but-i-still-feel-it", BookGenre.LIKED, 1, "Numb, But I Still Feel It", "Title Fight", Mode.MAJOR, "ABCAB",
            "iii–IV–I: припев, который тянется к дому",
            """
            A: | I | iii | IV | vi |
            B: | iii | IV | iii | IV | I | I |
            C: | Imaj7 | vi | iii | iii |
            """,
        ),
        entry(
            "earfquake", BookGenre.LIKED, 2, "EARFQUAKE", "Tyler, The Creator", Mode.PHRYGIAN, "AABA",
            "фригийский ♭IImaj⁷ над тоникой i⁷ и II⁷ в бридже",
            """
            A: | bIImaj7 | i7 | bvii7 | i7 |
            B: | bIImaj7 | i7 | bvii7 | II7 |
            """,
        ),
        entry(
            "just-like-heaven", BookGenre.LIKED, 1, "Just Like Heaven", "The Cure", Mode.MAJOR, "AABAAB",
            "I–V–ii–IV и ♭VII в припеве",
            """
            A: | I | V | ii | IV |
            B: | vi | bVII | vi | bVII |
            """,
        ),
        entry(
            "all-the-rage-back-home", BookGenre.LIKED, 1, "All the Rage Back Home", "Interpol", Mode.MAJOR, "AABAAB",
            "IV–vi–I: песня начинается не с дома",
            """
            A: | IV | vi | I | I |
            B: | IV | vi | V | V |
            """,
        ),
        // ---------------------------------------------------------------- pop & rock
        entry(
            "stand-by-me", BookGenre.POP, 1, "Stand by Me", "Ben E. King", Mode.MAJOR, "A",
            "I–vi–IV–V: дом, его грустная тень, подъём и возврат",
            "A: | I | I | vi | vi | IV | V | I | I |",
        ),
        entry(
            "no-woman-no-cry", BookGenre.POP, 1, "No Woman, No Cry", "Bob Marley", Mode.MAJOR, "A",
            "I–V–vi–IV — четыре аккорда половины поп-музыки",
            "A: | I | V | vi | IV |",
        ),
        entry(
            "let-it-be", BookGenre.POP, 1, "Let It Be", "The Beatles", Mode.MAJOR, "AB",
            "тот же I–V–vi–IV, а в припеве он же задом наперёд",
            """
            A: | I | V | vi | IV | I | V | IV | I |
            B: | vi | V | IV | I | I | V | IV | I |
            """,
        ),
        entry(
            "knockin", BookGenre.POP, 1, "Knockin' on Heaven's Door", "Bob Dylan", Mode.MAJOR, "A",
            "I–V–ii и I–V–IV: как одна смена аккорда меняет настроение",
            "A: | I | V | ii | ii | I | V | IV | IV |",
        ),
        entry(
            "twist-and-shout", BookGenre.POP, 1, "Twist and Shout", "The Beatles", Mode.MAJOR, "A",
            "I–IV–V — три главных аккорда лада",
            "A: | I IV | V |",
        ),
        entry(
            "sweet-home", BookGenre.POP, 1, "Sweet Home Alabama", "Lynyrd Skynyrd", Mode.MIXOLYDIAN, "A",
            "I–♭VII–IV: миксолидийский рок",
            "A: | I | bVII IV |",
        ),
        entry(
            "horse-no-name", BookGenre.POP, 1, "A Horse with No Name", "America", Mode.MINOR, "A",
            "i–♭VII: два аккорда, весь вайб в ступенях",
            "A: | i | bVII |",
        ),
        entry(
            "canon", BookGenre.POP, 2, "Канон ре мажор", "Иоганн Пахельбель", Mode.MAJOR, "A",
            "I–V–vi–iii–IV–I–IV–V: прародитель поп-прогрессий",
            "A: | I | V | vi | iii | IV | I | IV | V |",
        ),
        entry(
            "hit-the-road-jack", BookGenre.POP, 1, "Hit the Road Jack", "Ray Charles", Mode.MINOR, "A",
            "i–♭VII–♭VI–V: андалузская каденция",
            "A: | i bVII | bVI V |",
        ),
        entry(
            "zombie", BookGenre.POP, 1, "Zombie", "The Cranberries", Mode.MINOR, "A",
            "i–♭VI–♭III–♭VII: тот же поп-квадрат, но из минора",
            "A: | i | bVI | bIII | bVII |",
        ),
        entry(
            "hey-joe", BookGenre.POP, 2, "Hey Joe", "Jimi Hendrix", Mode.MAJOR, "A",
            "♭VI–♭III–♭VII–IV–I: квинтовый круг к дому",
            "A: | bVI bIII | bVII IV | I | I |",
        ),
        entry(
            "hotel-california", BookGenre.POP, 2, "Hotel California", "Eagles", Mode.MINOR, "A",
            "восемь аккордов минора: i–V–♭VII–IV–♭VI–♭III–iv–V",
            "A: | i | V | bVII | IV | bVI | bIII | iv | V |",
        ),
        // ---------------------------------------------------------------- blues
        entry(
            "blues-12", BookGenre.BLUES, 1, "Блюз, 12 тактов", null, Mode.MAJOR, "A",
            "I⁷–IV⁷–V⁷: форма, на которой стоит рок и джаз",
            "A: | I7 | I7 | I7 | I7 | IV7 | IV7 | I7 | I7 | V7 | IV7 | I7 | V7 |",
        ),
        entry(
            "minor-blues", BookGenre.BLUES, 2, "Минорный блюз", null, Mode.MINOR, "A",
            "i⁷–iv⁷ и поворот ♭VI⁷–V⁷",
            "A: | i7 | i7 | i7 | i7 | iv7 | iv7 | i7 | i7 | bVI7 | V7 | i7 | V7 |",
        ),
        entry(
            "jazz-blues", BookGenre.BLUES, 3, "Джазовый блюз", null, Mode.MAJOR, "A",
            "блюз с ii–V и вторичными доминантами",
            "A: | I7 | IV7 | I7 | v7 I7 | IV7 | #iv°7 | I7 | VI7 | ii7 | V7 | I7 VI7 | ii7 V7 |",
        ),
        // ---------------------------------------------------------------- jazz
        entry(
            "autumn-leaves", BookGenre.JAZZ, 2, "Autumn Leaves", "Joseph Kosma", Mode.MINOR, "AABC",
            "ii–V в мажоре и ii–V в миноре — азбука джаза",
            """
            A: | iv7 | bVII7 | bIIImaj7 | bVImaj7 | iiø7 | V7 | i | i |
            B: | iiø7 | V7 | i | i | iv7 | bVII7 | bIIImaj7 | bVImaj7 |
            C: | iiø7 | V7 | i7 | bVI7 | iiø7 V7 | i | i |
            """,
        ),
        entry(
            "blue-bossa", BookGenre.JAZZ, 2, "Blue Bossa", "Kenny Dorham", Mode.MINOR, "AB",
            "минорный ii–V–i и ii–V в чужую тональность (♭II)",
            """
            A: | i7 | i7 | iv7 | iv7 | iiø7 | V7 | i7 | i7 |
            B: | biii7 | bVI7 | bIImaj7 | bIImaj7 | iiø7 | V7 | i7 | iiø7 V7 |
            """,
        ),
        entry(
            "fly-me-to-the-moon", BookGenre.JAZZ, 2, "Fly Me to the Moon", "Bart Howard", Mode.MAJOR, "ABAC",
            "квинтовый круг: vi–ii–V–I–IV–viiø–III–vi",
            """
            A: | vi7 | ii7 | V7 | Imaj7 I7 | IVmaj7 | viiø7 | III7 | vi7 VI7 |
            B: | ii7 | V7 | Imaj7 vi7 | ii7 | V7 | Imaj7 | viiø7 | III7 |
            C: | ii7 | V7 | iii7 VI7 | ii7 | V7 | Imaj7 | Imaj7 | viiø7 III7 |
            """,
        ),
        entry(
            "all-of-me", BookGenre.JAZZ, 2, "All of Me", "Gerald Marks, Seymour Simons", Mode.MAJOR, "ABAC",
            "вторичные доминанты: III⁷, VI⁷, II⁷ ведут по кругу домой",
            """
            A: | I6 | I6 | III7 | III7 | VI7 | VI7 | ii7 | ii7 |
            B: | III7 | III7 | vi7 | vi7 | II7 | II7 | ii7 | V7 |
            C: | IV6 | iv6 | I6 iii7 | VI7 | ii7 | V7 | I6 | ii7 V7 |
            """,
        ),
        entry(
            "a-train", BookGenre.JAZZ, 2, "Take the A Train", "Billy Strayhorn", Mode.MAJOR, "AABA",
            "II⁷ — мажорный аккорд на второй ступени, и мост на IV",
            """
            A: | I6 | I6 | II7 | II7 | ii7 | V7 | I6 | ii7 V7 |
            B: | IVmaj7 | IVmaj7 | IVmaj7 | IVmaj7 | II7 | II7 | ii7 | V7 |
            """,
        ),
        entry(
            "satin-doll", BookGenre.JAZZ, 3, "Satin Doll", "Duke Ellington", Mode.MAJOR, "AABA",
            "цепочка ii–V, которая съезжает на полтона",
            """
            A: | ii7 | V7 | iii7 | VI7 | vi7 II7 | bvi7 bII7 | Imaj7 | Imaj7 |
            B: | v7 | I7 | IVmaj7 | IVmaj7 | vi7 | II7 | ii7 | V7 VI7 |
            """,
        ),
        entry(
            "rhythm-changes", BookGenre.JAZZ, 3, "Rhythm Changes", "по «I Got Rhythm», George Gershwin", Mode.MAJOR, "AABA",
            "I–vi–ii–V по кругу и мост из доминант III⁷–VI⁷–II⁷–V⁷",
            """
            A: | I vi7 | ii7 V7 | I vi7 | ii7 V7 | I I7 | IV #iv°7 | I V7 | I |
            B: | III7 | III7 | VI7 | VI7 | II7 | II7 | V7 | V7 |
            """,
        ),
        entry(
            "so-what", BookGenre.JAZZ, 1, "So What", "Miles Davis", Mode.DORIAN, "AABA",
            "модальный джаз: один дорийский аккорд и сдвиг на полтона",
            """
            A: | i7 | i7 | i7 | i7 | i7 | i7 | i7 | i7 |
            B: | bii7 | bii7 | bii7 | bii7 | bii7 | bii7 | bii7 | bii7 |
            """,
        ),
        entry(
            "cantaloupe-island", BookGenre.JAZZ, 1, "Cantaloupe Island", "Herbie Hancock", Mode.DORIAN, "A",
            "i⁷–♭VI⁷–vi⁷: три аккорда по четыре такта",
            "A: | i7 | i7 | i7 | i7 | bVI7 | bVI7 | bVI7 | bVI7 | vi7 | vi7 | vi7 | vi7 | i7 | i7 | i7 | i7 |",
        ),
    )

    fun byId(id: String): BookEntry? = ENTRIES.firstOrNull { it.id == id }

    /** Library ids of book pieces are stable, so a piece is never added twice. */
    fun libraryId(entry: BookEntry): String = "book:${entry.id}"

    private fun entry(
        id: String,
        genre: BookGenre,
        difficulty: Int,
        title: String,
        artist: String?,
        mode: Mode,
        form: String,
        teaches: String,
        progression: String,
    ) = BookEntry(
        id = id,
        genre = genre,
        difficulty = difficulty,
        teaches = teaches,
        song = Song(
            id = "book:$id",
            title = title,
            artist = artist,
            mode = mode,
            sections = ProgressionText.parseSections(progression.trimIndent()),
            form = ProgressionText.parseForm(form),
            notes = "Из встроенного сборника: $teaches. Гармония упрощена — поправь под свою версию.",
        ),
    )
}
