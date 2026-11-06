package com.kazaeza.muzykalka.music

/** Shelf of the built-in songbook. */
enum class BookGenre(val ru: String) { POP("поп и рок"), BLUES("блюз"), JAZZ("джаз") }

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
 */
object SongBook {
    val ENTRIES: List<BookEntry> = listOf(
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
