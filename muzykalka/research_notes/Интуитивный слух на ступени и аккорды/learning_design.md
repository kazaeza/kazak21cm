# Learning-science and product-design principles for a durable scale-degree / chord / key ear-training app

Scope note (read first). This session ran ~17 tool calls. Search tools returned abstract/snippet-level summaries; two PDF fetches (Mettler & Kellman 2014; Nichols & Springer 2025) came back as unreadable binary, so those are cited only from search-result summaries/metadata. Everything under "Cited Findings" is traceable to a returned search result; the "Inferences" are design proposals by the researcher, not findings. Several topics (multimodal encoding, motivation/gamification, confusion-matrix diagnostics, Ericsson deliberate practice, Bjork "desirable difficulties" originals, scaffolding fade-out) got NO primary-source retrieval in this session; they are listed under Gaps with an explicit "from background knowledge, unverified here" tag so the report writer can decide whether to fetch them. Evidence-strength tags: [STRONG] replicated/meta-analytic; [MODERATE] a few good studies or a review seen only at abstract level; [WEAK] single study, vendor claim, or inference; [NONE] no source found.

---

## 1. Feedback design (what happens after a wrong answer; timing; elaboration; time-on-feedback; self-explanation)

### Takeaway
Feedback that carries information (correct answer + why/how) beats bare right/wrong; for an error the learner must at least be shown the correct answer, and immediate vs delayed timing evidence is mixed and task-dependent (immediate tends to favor hard tasks/lower-skill learners). For this app's existing "play the correction at once" rule the literature supports keeping it, and suggests adding a brief, explicit contrast between what the user chose and the correct item (elaboration) rather than changing timing. No ear-training-specific feedback-timing study was found.

### Cited Findings
- Meta-analysis of 435 studies (k = 994, N > 61,000): feedback overall d = 0.48 on learning, with large heterogeneity; the information content of the feedback is a key moderator, and low-information feedback yields low effects. [MODERATE-STRONG] — [Wisniewski, Zierer & Hattie 2020, Frontiers in Psychology](https://pmc.ncbi.nlm.nih.gov/articles/PMC6987456) (also [PDF](https://www.frontiersin.org/articles/10.3389/fpsyg.2019.03087/pdf))
- Same meta-analysis: feedback had higher impact on cognitive and motor-skill outcomes than on motivational/behavioral outcomes (i.e., feedback is a learning tool more than a motivation tool). — [Wisniewski et al. 2020](https://pmc.ncbi.nlm.nih.gov/articles/PMC6987456)
- Shute (2008) review, Review of Educational Research 78(1):153-189: guideline to give elaborated feedback describing "the what, how, and why" (cognitive feedback typically more effective than verification of result) and to present it in manageable chunks so the learner is not overwhelmed. Quote is as reproduced in a secondary summary; check primary. — [Shute 2008 PDF](https://myweb.fsu.edu/vshute/pdf/shute%202008_b.pdf)
- Shute's timing section (as summarized secondhand, exact Table 4 wording NOT verified): immediate feedback tends to be more effective for difficult tasks/lower achievers; delayed feedback may serve better for easier tasks/higher achievers; optimal timing depends on task nature; complicated or motor-skill tasks listed among cases favoring immediate. [MODERATE, secondhand] — [Shute 2008 PDF](https://myweb.fsu.edu/vshute/pdf/shute%202008_b.pdf); secondary summary: [eLearning Industry digest](https://elearningindustry.com/providing-feedback-dos-donts)
- Metcalfe (2017), "Learning from Errors", Annual Review of Psychology 68:465-489: errorful learning followed by corrective feedback is beneficial; high-confidence errors are corrected more readily than low-confidence errors (hypercorrection); corrective feedback including analysis of the reasoning behind the mistake is crucial; feedback need not be immediate but must supply the correct answer, not only a right/wrong flag; low-stakes settings are the place to let errors happen. [MODERATE; abstract-level + secondary] — [Metcalfe 2017 (bibbase record)](https://bibbase.org/network/publication/metcalfe-learningfromerrors-2017), DOI 10.1146/annurev-psych-010416-044022
- A Memory & Cognition hypercorrection paper (as described in a search summary) found the hypercorrection effect unchanged when feedback delay was increased; a child study found no main effect of 5-min-delayed vs immediate feedback; the summary notes timing results across studies are mixed. [WEAK-MODERATE; snippet-level, authors not confirmed] — [Springer s13421-013-0344-3](https://link.springer.com/content/pdf/10.3758/s13421-013-0344-3.pdf)
- A companion study from Zatorre's group (via search summary): perceptual-discrimination training alone was not sufficient to improve vocal accuracy in non-musicians — i.e., hearing-only feedback does not automatically fix production. [WEAK-MODERATE] — [Frontiers 2012, micromelody training](https://www.frontiersin.org/journals/psychology/articles/10.3389/fpsyg.2012.00544/full) (search summary pointed to a companion "Vocal accuracy ... micromelody discrimination training" record: [record](https://wicri-demo.istex.fr/Wicri/Musique/explor/OperaV1/Site/fr/Main/Exploration/bibRecord.php?hk=000D33))

### Inferences
- The app's "resolution playback" on wrong answers satisfies "supply the correct answer". The evidence-backed addition is *information about the error*, not faster/slower timing: e.g., after a miss, play the user's chosen degree (in the same key/drone) then the correct one back-to-back ("you heard 6, you said 4 — 6 leans down to 5, 4 leans down to 3"), i.e., a contrast pair plus a one-line pull/tendency tag. This is a design hypothesis; no ear-training study directly tests contrast-vs-correct-only playback.
- Hypercorrection implies confidence capture could be valuable: a cheap one-tap "sure / guess" flag before the answer (or latency as proxy) lets the scheduler prioritise confident errors (most correctable, most diagnostic) — design hypothesis built on Metcalfe's finding; ear-training specific evidence not found.
- Chunking (Shute): after an error keep elaboration to one visual tag + one audio contrast, not a lesson; fits the 60-90 s round.
- "Feedback must not be the only motivational lever": Wisniewski finds feedback helps cognitive/motor outcomes more than motivation, so motivation must be designed separately (section 6).
- Because the existing design already plays correction immediately, delayed feedback is NOT recommended as a main strategy; at most an optional "retry-from-memory before reveal" (see Sequencing) which is separate from feedback timing.

### Gaps
- Time-on-feedback (how long the user should dwell on the corrected item, e.g., forced replay count or pause length): no source found. Background knowledge (unverified): dwelling after errors correlates with learning in some tutoring studies; not retrieved.
- Self-explanation evidence (Chi et al.; Dunlosky "Improving Students' Learning" ratings of self-explanation as moderate utility): not searched; no ear-training evidence. Treat as [NONE] for this domain.
- Butler & Roediger (2008) timing and Kulhavy / Hattie-Timperley foundational papers: not retrieved.
- Direct evidence that "contrast replay (given vs correct)" beats "correct-only replay" for pitch/chord categories: not found.

---

## 2. Sequencing (variability, interleaving vs blocking, contextual interference, mastery thresholds, progressive contrast, scaffold fade-out, transfer-appropriate processing)

### Takeaway
For perceptual category learning, interleaving usually beats blocking, especially when categories are similar (the classic perceptual-confusion case), though there are boundary conditions and learners wrongly judge blocking as better; adaptive, performance-based sequencing that spaces categories by response time/accuracy beat random and mini-block orders and generalised to novel items. Auditory-learning research shows generalisation is limited and lags behind learning, so variability of surface (timbre, voicing, key, register) should be built in rather than assumed to transfer.

### Cited Findings
- Kornell & Bjork (2008) (painting-style induction): interleaved exemplars gave better identification than blocked (59% vs 36% correct reported in one experiment); benefit extended to other categories (birds, children) and to between- and within-subject designs. [STRONG for visual categories; abstract-level here] — [Kornell & Bjork via Psychological Research review](https://link.springer.com/10.3758/s13421-020-01074-w); [Firth et al. 2021 systematic review of interleaving](https://strathprints.strath.ac.uk/75711/7/Firth_etal_RE_2021_A_systematic_review_of_interleaving.pdf)
- Boundary conditions: inserting 10 s of unrelated trivia between exemplars removed the interleaving benefit (Birnbaum et al. 2013); blocking has advantages in some perceptual tasks (Goldstone 1996); an active-vs-passive study found active learning benefits from interleaving while passive learning benefits from blocking. [MODERATE] — [Psychological Research review](https://link.springer.com/10.3758/s13421-020-01074-w)
- Metacognitive illusion: only 19% of younger adults (4% of older) judged interleaving superior in Kornell et al. 2010; Yan, Bjork & Bjork (2016) found learners believed blocking was better even after interleaving had helped them. — [Yan, Bjork & Bjork 2016 PDF](https://sites.lifesci.ucla.edu/psych-bjorklab/wp-content/uploads/sites/13/2016/11/YanBjorkBjork2016.pdf); [Psychological Research review](https://link.springer.com/10.3758/s13421-020-01074-w)
- Mechanism: discriminative-contrast account (interleaving juxtaposes categories so distinguishing features stand out) vs attentional-bias account (blocking helps notice within-category similarity). Sana et al.: robust interleaving benefit across perceptual and text categories, not moderated by working-memory capacity. [MODERATE] — [Psychological Research review](https://link.springer.com/10.3758/s13421-020-01074-w); [Sana et al. abstract (McMaster)](https://experts.mcmaster.ca/scholarly-works/1731614)
- Mettler & Kellman (2014, Vision Research 99:111-123): 12 butterfly genera; adaptive response-time-based category sequencing (spacing derived from memory research) was reliably better in learning efficiency than random presentation and than mini-blocks of 3 exemplars; effects persisted over a 1-week delay and were enhanced for novel items; Exp. 2 found even larger adaptive-sequencing effects for lower-variability categories. [MODERATE; from search summary because PDF unreadable] — [Mettler & Kellman 2014](https://kellmanlab.psych.ucla.edu/files/mettler_kellman_2014.pdf); [Kellman lab](https://kellmanlab.psych.ucla.edu/research/adaptive-learning-and-learning-technology/)
- Goldstone (1998, Annual Review of Psychology 49:585-612): perceptual learning is long-lasting change in the perceptual system; four mechanisms — attention weighting, imprinting, differentiation, unitization. This is the framework behind "contrast" designs. [Foundational review] — [Goldstone 1998 page](https://pcl.sitehost.iu.edu/rgoldsto/annual.html)
- Overlearning vs spacing (math, not perceptual): 10 problems in one session vs split across two sessions a week apart: distributed practice effect nil at 1-week test but very large at 4-week test; extra massed repetition (3 vs 9 problems) had no effect on retention at 1 or 4 weeks. [MODERATE, adults/math] — [Rohrer & Taylor 2006](https://digitalcommons.usf.edu/psy_facpub/1770/)
- Rohrer & Taylor 2007 "The shuffling of mathematics problems improves learning" exists (interleaving in practice sets); details not retrieved. — [same search result list](https://digitalcommons.usf.edu/psy_facpub/1770/)
- Auditory perceptual learning generalisation: learning is typically specific, generalisation to novel stimuli is mostly limited to spectral features; learning can generalise across frequency/ear/duration; AM-detection learning generalised across rates but not to a new task (rate discrimination); generalisation lags behind learning (longer training increased the number of conditions to which learning generalised); but a 2020 study found larger trained-condition gains came with smaller generalisation. [MODERATE; secondhand citations of Wright & Zhang 2009 etc.] — [PMC3745496](https://pmc.ncbi.nlm.nih.gov/articles/PMC3745496); [Generalization lags behind learning (McMaster)](https://experts.mcmaster.ca/display/publication1643919); [Practice makes transfer imperfect](https://cris.haifa.ac.il/en/publications/practice-makes-transfer-imperfect-evidence-from-auditory-learning/)
- Pitch-training generalisation in non-musicians: two weeks of micromelody training showed learning on trained stimuli and generalisation to other frequencies; reported as supporting configural/higher-order learning, with a transposition-task transfer test. [MODERATE] — [Zatorre et al. 2012, Frontiers in Psychology](https://www.frontiersin.org/journals/psychology/articles/10.3389/fpsyg.2012.00544/full)
- Practitioner position (not evidence): vendors argue isolated interval drills do not transfer and tonal-centre (functional) training does; they cite no studies. [WEAK] — [Fifths blog](https://fifths-marketing.pages.dev/blog/functional-vs-interval-ear-training/)

### Inferences
- "Fixed key per block vs random key every item": interleaving evidence supports mixing *categories* (degrees/chords that are mutually confusable) within a block. Key variation is a *surface* variable; auditory-generalisation work implies key/timbre/register should vary over sessions so the user cannot hang the answer on absolute pitch, but varying key on every trial adds a re-orientation cost (the drone/cadence re-establishes the tonic). A defensible schedule: block = one key for N trials (the drone is the scaffold), degrees interleaved within; change key between blocks and across days; gradually shorten block (N=12 → 6 → 3 → 1). This exact schedule is NOT tested anywhere found; it is an inference from interleaving + specificity literature.
- Progressive-contrast ("start with 2-3 contrasting items, then add"): consistent with the discriminative-contrast mechanism (contrast needs similar-but-distinct pairs) and with Kellman's adaptive sequencing; start with maximally different pairs (e.g., 1 vs 5 vs 7), then add confusable ones (e.g., 2 vs 4, 6 vs 7, minor third vs major). Interleave from the start (no blocked-by-degree phase), but keep the active set small. No source found directly testing this for scale degrees.
- Because learners prefer blocking (metacognitive illusion) the app should not ask the user to choose schedule; hide the interleave inside "today's round".
- Adaptive spacing within a round (Mettler-Kellman style): re-present an item sooner after an error and push it later after fast correct answers; response time is a usable mastery signal (assumption transferred from visual PL).
- Boundary condition to respect: the trivia result implies that long interruptions (e.g., a lengthy explanatory screen between trials) can erase the interleaving juxtaposition benefit; keep inter-item gaps short.
- Transfer-appropriate processing: since generalisation is mostly spectral and task-specific, include real-song timbres/voicings and the actual task format (label function while harmony plays under melody/band) as a *fraction* of practice, not only at the end. No ear-training source found that quantifies this.
- Overlearning in one session gives little retention benefit (math); in a 60-90 s round format that argues for stopping at the round end and re-seeing the item later rather than extending drills of an already-solid item.

### Gaps
- Mastery-learning thresholds (e.g., 90% over N trials vs spaced re-checks) for perceptual skills: no source retrieved. Existing app has `Graduation` thresholds; not examined here.
- Contextual interference (Shea & Morgan, Magill & Hall) for *perceptual-motor* vs pure perceptual tasks: not searched. Background knowledge (unverified): CI benefit is strongest in motor tasks.
- Evidence specifically for drone/cadence/reference fade-out or "scaffolding fade" in ear training: [NONE]. Background knowledge (unverified): expertise-reversal effect (Kalyuga) suggests guidance should fade as skill grows; not retrieved.
- Evidence that tonal-context (functional) training outperforms isolated-interval training in controlled trials: [NONE] — only vendor claims found; the closest academic hits (Nichols & Springer 2025, "Piano history, aural skills, and working memory predict melodic dictation performance", Frontiers in Psychology 16:1579714, DOI 10.3389/fpsyg.2025.1579714) are correlational predictors of dictation; contents not read (title/metadata only) — [PDF](https://www.frontiersin.org/journals/psychology/articles/10.3389/fpsyg.2025.1579714/pdf).
- Ericsson deliberate practice: not retrieved.

---

## 3. Retention (spaced repetition for perceptual items, FSRS/SM-2 applicability, sleep/consolidation, session length/frequency)

### Takeaway
Distributed practice is well supported (math adults; perceptual category sequencing with 1-week delayed benefit), and perceptual learning shows a sleep-dependent consolidation component plus a risk that over-training in a single session stops adding (or interferes with) learning — supporting short rounds spread over days. FSRS is content-agnostic and can schedule any item that can be graded, but there is no evidence that its fitted parameters suit perceptual-category items, and community opinion holds that scheduling per se is unproven for skills.

### Cited Findings
- Karni & Sagi (texture discrimination): no improvement after up to 8 h of wake, significant improvement after a night of sleep; Karni et al. 1994 (Science) reported REM-sleep deprivation blocked overnight gains while slow-wave disruption did not. [MODERATE; visual PL, 1990s; REM-specificity debated] — [Dependence on REM sleep of overnight improvement of a perceptual skill](https://cris.haifa.ac.il/en/publications/dependence-on-rem-sleep-of-overnight-improvement-of-a-perceptual--2/); search results also pointed to [PMC3441080](https://pmc.ncbi.nlm.nih.gov/articles/PMC3441080), [PMC4041809](https://pmc.ncbi.nlm.nih.gov/articles/PMC4041809), [Matarazzo et al. 2008](https://orbi.uliege.be/bitstream/2268/7336/1/Matarazzo-2008-jov-8-4-7.pdf) (per-source claim mapping not verified)
- Overtraining: Censor, Karni & Sagi are cited for learning benefiting from more trials up to a point after which further trials reduce learning; a sleep/nap reduces adaptation from excessive training; Ashley & Pearson attribute overtraining effects to disruption of wake-time consolidation. Retroactive interference before consolidation impaired memory more than proactive; rescued learning correlated with REM amount. [MODERATE; visual PL; mechanisms disagree — some sources REM, others non-REM or wake consolidation] — same links as above ([PMC4704701](https://pmc.ncbi.nlm.nih.gov/articles/PMC4704701), [PMC3586999](https://pmc.ncbi.nlm.nih.gov/articles/PMC3586999) also returned)
- Pitch discrimination: non-musicians' thresholds ~6x those of classical musicians initially, ~4x after 2 h of training; 4-8 h of training were needed to reach musicians' thresholds (30 vs 30 participants). Indicates that large perceptual gains are achievable in single-digit hours of focused training for fine discrimination. [MODERATE] — [Micheyl et al. 2006](https://experts.umn.edu/en/publications/influence-of-musical-and-psychoacoustical-training-on-pitch-discr/)
- Spacing (adult math): large effect of distributed practice at 4-week retention; no benefit of extra same-session repetitions. [MODERATE] — [Rohrer & Taylor 2006](https://digitalcommons.usf.edu/psy_facpub/1770/)
- Adaptive perceptual category sequencing retained advantage at a 1-week delay. — [Mettler & Kellman 2014](https://kellmanlab.psych.ucla.edu/files/mettler_kellman_2014.pdf)
- FSRS is content-agnostic and deck-agnostic, which makes integration easy but means the model will underfit a bit (does not model content type). — [Domenic, "Spaced repetition recap"](https://domenic.me/fsrs/)
- An Anki forum answer: use FSRS to memorise card content, but for practicing skills there is no evidence that either algorithm or spaced-repetition scheduling in general is what is needed (opinion). [WEAK] — [Anki forums thread](https://forums.ankiweb.net/t/questions-about-tuning-fsrs-for-math-logical-reasoning-problem-cards/70949)
- Borretti: spaced repetition works best for systematically organised, unambiguous key-value information with short keys/values (perceptual items not addressed directly). [WEAK/opinion] — [Borretti](https://borretti.me/article/the-applicability-of-spaced-repetition)
- Kellman-lab adaptive learning (Mettler & Kellman) is explicit prior art for spacing algorithms applied to perceptual categories — [Kellman lab page](https://kellmanlab.psych.ucla.edu/research/adaptive-learning-and-learning-technology/)

### Inferences
- FSRS "item" for this app should be a *category-in-context* (e.g., "degree 6 over a major drone in guitar timbre", or a confusable pair/"duel") rather than a particular audio clip; otherwise it schedules memory of exemplars rather than the perceptual skill. Hybrid: FSRS for long-interval (days) category scheduling + a Mettler-Kellman-style within-round re-queue for short-interval (seconds-minutes) sequencing. Neither FSRS nor SM-2 has published validation on perceptual items in what I found, so the Anki-forum caution applies: monitor retention calibration on this user's own data (single user = can fit per-user parameters but with small data).
- Grading proxy: perceptual items are binary correct/incorrect plus latency; mapping latency to FSRS "Hard/Good/Easy" is an unverified design choice (Mettler-Kellman use response time in sequencing, supporting latency as a mastery signal).
- Session design: given overtraining/interference findings and distributed-practice results, 60-90 s rounds, a few per day at separated times, with a night's sleep between introducing a new contrast and the first review, is consistent with the evidence. The exact numbers (rounds/day, minimum gap) are not established for music; treat as hypotheses to A/B on the single user.
- Practice right before sleep might help (consolidation), but evidence is visual-task, mixed on mechanism; do not build a feature around it.

### Gaps
- Direct tests of FSRS/SM-2 forgetting curves on perceptual items: none found.
- Auditory (musical) sleep-dependent consolidation studies for chord/degree recognition: not found; evidence is from visual texture discrimination and general auditory PL.
- Optimal session length/frequency for perceptual learning: no specific number found (Watanabe & Sasaki "perceptual learning" reviews and task-irrelevant PL were not retrieved).
- Fahle's perceptual learning review: not retrieved.

---

## 4. Multimodal encoding (singing back, playing back, color mapping, gesture, mnemonics, pull/gravity metaphors)

### Takeaway
Very little was retrieved. One relevant finding: perception-only pitch training did not improve non-musicians' vocal accuracy, which cautions that "sing it back" must be trained as production, and may not be gained from perception drills alone (and vice versa is untested here). No credible source was found for color-coding, gesture, mnemonics or tendency metaphors within this session.

### Cited Findings
- Perceptual discrimination training alone was insufficient to improve vocal accuracy in non-musicians (companion study of Zatorre's group; search summary). [WEAK-MODERATE] — [micromelody training record](https://wicri-demo.istex.fr/Wicri/Musique/explor/OperaV1/Site/fr/Main/Exploration/bibRecord.php?hk=000D33)
- Micromelody training generalised across frequencies and to a transposition task, with fMRI changes in right posterior auditory cortex (configural coding of pitch-direction patterns). Supports the idea that melody-shape/relative-pitch coding is learnable and not tied to absolute frequency. [MODERATE] — [Zatorre et al. 2012](https://www.frontiersin.org/journals/psychology/articles/10.3389/fpsyg.2012.00544/full)
- Goldstone's framework lists "unitization" and "differentiation" as perceptual-learning mechanisms; one reading is that a visual label/colour that is consistently paired with a category could support attention weighting/differentiation, but the review summary retrieved does not itself test colour coding. — [Goldstone 1998](https://pcl.sitehost.iu.edu/rgoldsto/annual.html)

### Inferences
- Singing/guitar production as an *additional* response channel for the same category (sing the degree over drone, play it on the guitar) is plausibly useful for guitarists as it ties auditory category to motor/fretboard action, but the cited finding warns that listening practice does not by itself yield accurate singing; treat production as a separate trained skill, not a free by-product of perceptual drills. Evidence that production practice improves *perception* of degrees was not found.
- Colour mapping already exists in the app (`DegreeColors`); evidence for its effect on durable aural learning was not found — recommend it as a labelling aid that is faded in perceptual-only tests (to avoid the colour becoming the cue instead of the sound). Fading rule is an inference from the transfer-appropriate-processing principle, not from a colour study.
- Tendency/"pull"/gravity metaphors: no empirical test located; treat as pedagogical convention, cheap to include in the elaborative feedback tag.

### Gaps
- Evidence for singing-back, solfège/hand signs (Kodály), colour-hearing, gesture, mnemonics ("anchor songs" for scale degrees) improving ear-training outcomes: NOT retrieved. Background knowledge (unverified here): small studies on Kodály/solfège and on song anchors exist and are mostly weak-quality; do not cite without retrieval.
- Evidence for tendency-tone / "musical forces" metaphors (Larson; Lerdahl; Huron "Sweet Anticipation" on scale-degree qualia): not searched.
- Embodied cognition effects (movement) specific to pitch learning: not searched.

---

## 5. Motivation (streaks, short rounds, personal bests, loss aversion, flow/adaptive difficulty, social, gamification pitfalls)

### Takeaway
Only weak, company-sourced evidence on streaks was retrieved; no credible evidence was found about gamification pitfalls (guessing, speed pressure) or flow-based difficulty in ear training. The one robust structural point available is that feedback meta-analysis finds feedback better at cognitive than motivational outcomes, so engagement features must be treated as separate from learning mechanisms and checked against learning metrics.

### Cited Findings
- Duolingo's streak counts consecutive days with at least one exercise and resets on a missed day; a secondary article reports ~9 million users with a 1-year+ streak, a company metric CURR (share of today's active users also active in the past week) and ~600 streak-related tests over two years. [WEAK; secondhand company disclosures] — [Audiencers article](https://theaudiencers.com/subscription-models-live-off-habits-lessons-from-duolingos-retention-success/)
- Secondhand write-up of four 2022 Duolingo retention experiments (widgets, streak icons, commitment copy, "Perfect Streak Week"). [WEAK] — [Lazyweb summary](https://lazyweb.com/research/duolingo-retention-experiments)
- Claims of "3x higher 30-day retention" for streak users circulate on secondary sites; no primary source found. [UNVERIFIED] — see [Lazyweb](https://lazyweb.com/research/duolingo-retention-experiments)
- A thesis (146 valid cases, logistic regression) on whether streak length predicts continued use without reminders exists; peer-review status unconfirmed. [WEAK] — [Univ. of Pavia thesis](https://unitesi.unipv.it/retrieve/f413802b-7e87-44ae-9389-5cab285411d5/THESIS_ROKSANA.pdf)
- Duolingo half-life regression (Settles & Meeder 2016, ACL) is the spaced-repetition model; I did not retrieve the paper. — [analysis page](https://deepaksood619.github.io/education/competitors/duolingo-analysis)
- Feedback has higher effects on cognitive/motor outcomes than on motivation/behaviour. — [Wisniewski et al. 2020](https://pmc.ncbi.nlm.nih.gov/articles/PMC6987456)

### Inferences
- A streak defined as "one 60-90 s round" fits the round length, but there is no retrieved evidence that streaks improve *learning* (only usage); to avoid rewarding mindless taps, award streak/credit for rounds that include an accuracy or review-completion condition. Design hypothesis.
- Guessing and speed pressure: the app should not reward raw speed; use latency only as a hidden mastery signal (Mettler-Kellman). The pitfall claim itself is not sourced here.
- Single-user app: social features (leaderboards) are largely moot; "personal best" and a visible "confusions fixed" trend are the available levers; no evidence retrieved for efficacy.

### Gaps
- Loss aversion, personal-best effects, self-determination theory, Csikszentmihalyi flow, adaptive difficulty "85% rule" (Wilson et al. 2019): not searched; background knowledge unverified here.
- Gamification meta-analyses on learning outcomes (e.g., Sailer & Homner 2020; Hamari et al.): not searched.
- Primary Duolingo research (Settles & Meeder; Duolingo blog on streaks/Hard-mode): not retrieved.

---

## 6. Diagnostics (confusion matrices, adaptive weighting of weak confusions, duel drills, error-driven practice)

### Takeaway
No source on confusion-matrix–driven scheduling in ear training was found. What exists supports the mechanism underlying it: contrast between *similar* categories drives discrimination learning (interleaving effect larger for similar categories), and adaptive sequencing based on the learner's own performance beats random order.

### Cited Findings
- Interleaving helps most when categories are highly similar (the discriminative-contrast account). — [Psychological Research review](https://link.springer.com/10.3758/s13421-020-01074-w); [Firth et al. 2021](https://strathprints.strath.ac.uk/75711/7/Firth_etal_RE_2021_A_systematic_review_of_interleaving.pdf)
- Performance-adaptive category sequencing (response time) beat random order and mini-blocks in learning efficiency and novel-item generalisation. — [Mettler & Kellman 2014](https://kellmanlab.psych.ucla.edu/files/mettler_kellman_2014.pdf)
- High-confidence errors are the ones most readily corrected, so they are high-yield targets. — [Metcalfe 2017](https://bibbase.org/network/publication/metcalfe-learningfromerrors-2017)

### Inferences
- Log (presented → chosen) pairs per user; the confusion matrix is just the counts of off-diagonal cells. A "duel" drill = repeated binary choice between the two cells with highest weighted off-diagonal mass (already in this app as S-03 "Дуэль"). The literature found supports the *form* (similar-category contrast + adaptivity) but not a specific threshold such as "10 correct in a row".
- Weight selection by (error count × recency) and confidence (hypercorrection) rather than error count only; keep a floor on easy items to avoid a purely failure-focused experience (design hypothesis).
- Confusions in tonal ear training are probably structured by function (e.g., stable vs unstable degrees; neighbours a step apart) — that is domain knowledge/assumption, not retrieved. The app's own logs would show this; analyse before hard-coding which pairs to duel.
- Use the matrix also as a *transfer* diagnostic: compare confusions in synthetic drone drills vs real-song loops; divergence reveals reliance on cues specific to the training context.

### Gaps
- Empirical literature on confusion patterns for scale-degree identification (e.g., Arthur 2018 on scale-degree ID; Huron): not found/searched.
- Evidence comparing error-weighted vs uniform sampling in perceptual training: none retrieved apart from Kellman adaptive results.

---

## 7. Transfer assessment (does skill carry to real music; in-app transfer tasks)

### Takeaway
No controlled study showing that scale-degree/interval ear-training transfers to real-music dictation was found; auditory perceptual-learning research suggests generalisation is limited, task-specific and slow, so transfer should be measured and trained explicitly. Real-music transfer tasks can be built as labelling short real-song-like loops, but their validity is untested.

### Cited Findings
- Search for controlled evidence of scale-degree training transfer to real music returned none; only vendor/teacher claims and tangential academic items. [NONE for controlled evidence] — [Ear training search results; e.g., Fifths blog (vendor)](https://fifths-marketing.pages.dev/blog/functional-vs-interval-ear-training/)
- Nichols & Springer (2025): paper on piano history, aural skills and working memory predicting melodic dictation (keywords: melodic dictation, interval identification, attention, auditory imagery, working memory). Findings not read; indicates that working-memory/auditory-imagery load influences dictation, which is a plausible confound when a transfer task uses long real melodies. [Title/keywords only] — [Frontiers 2025 PDF](https://www.frontiersin.org/journals/psychology/articles/10.3389/fpsyg.2025.1579714/pdf)
- Auditory learning transfers imperfectly: learning generalises across some physical dimensions but not necessarily to new tasks; generalisation lags behind learning. — [Practice makes transfer imperfect](https://cris.haifa.ac.il/en/publications/practice-makes-transfer-imperfect-evidence-from-auditory-learning/); [Generalization lags behind learning](https://experts.mcmaster.ca/display/publication1643919)
- Mettler & Kellman measured transfer to *novel exemplars* (not only trained ones) with a 1-week delay — a usable template for transfer assessment (held-out items, delayed test). — [Mettler & Kellman 2014](https://kellmanlab.psych.ucla.edu/files/mettler_kellman_2014.pdf)
- Micromelody training: tested transfer to an untrained melody transposition task. — [Zatorre et al. 2012](https://www.frontiersin.org/journals/psychology/articles/10.3389/fpsyg.2012.00544/full)
- Search noted a teaching-design paper on aural skills using popular-music repertoire to encourage far transfer, outcomes not reported in the excerpt. [WEAK] — see search hit list (core.ac.uk/download/212694438.pdf)

### Inferences
- Protocol for the single user: (a) held-out novel items never used for scheduling (new timbre/voicing/key/song loops), (b) delayed tests (≥1 week, per Mettler-Kellman), (c) same response format as practice (name the degree/function), (d) separate "synthetic-drone accuracy" from "real-loop accuracy" and track the gap as the transfer metric.
- Task ideas (all untested hypotheses): "name the function of the chord at the cue" in a 4-8 s loop with real-song-like voicings and a band timbre; "which degree is the sustained melody note over this chord"; "finish the progression" (already exists: «Куда пойдёт?»); "whose harmony?" (exists: «Чья гармония?»); sing-along/ play-along check with the mic (exists in part). Avoid loops in memorised/known recordings if the goal is skill (recognition of the song instead of the harmony), or deliberately use known songs only as the anchor condition.
- Keep working-memory load low for transfer loops (short, one answer), to avoid confounding hearing with memory (Nichols & Springer suggests WM matters in dictation).
- Because generalisation lags learning, a good schedule alternates drill-format practice with transfer-format practice rather than saving transfer for the end (inference).

### Gaps
- Any controlled ear-training transfer trial (e.g., "functional hearing" vs interval training; Karpinski, Hoppe et al.): not found. Search tool suggested checking Music Perception / Psychology of Music with queries like "melodic dictation transfer", "tonal context interval identification", "solfège training outcomes" — not done.
- Validated instruments for "chord function in a clip" tests: none found.

---

## Cross-cutting summary of evidence quality (for the report writer)
- Strongest, most transferable: interleaving > blocking for similar perceptual categories (replicated, with boundary conditions); informative feedback > verification-only (meta-analysis); distributed > massed practice (adult math, perceptual sequencing); adaptive performance-based sequencing in perceptual learning (Kellman lab; one paper seen at abstract level).
- Moderate but from other modalities: sleep-dependent consolidation and overtraining effects (visual texture discrimination); auditory generalisation limits.
- Weak/absent for ear-training specifically: transfer to real music, drone/cadence fading, colour/gesture/singing as encoding aids, tendency metaphors, streak/gamification effects on learning, FSRS validity on perceptual items.
- Items needing retrieval before citing in a final report: Bjork "desirable difficulties" (1994/2011), Roediger & Karpicke (2006) testing effect, Kornell 2009 spacing vs massing, Butler & Roediger 2008 feedback timing, Dunlosky et al. 2013 (self-explanation/interleaving/practice testing ratings), Ericsson 1993 deliberate practice, Kalyuga expertise reversal, Settles & Meeder 2016, Ye et al. FSRS papers (KDD 2022/IEEE TKDE 2023), Wilson et al. 2019 "85% rule", Wright & Zhang 2009, Fahle 2005, Watanabe & Sasaki 2015.
