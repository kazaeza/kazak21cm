"""Generate golden vectors for the Kotlin FSRS-6 port (core/srs).
Scheduler config mirrors the app: no learning/relearning steps, no fuzz, day granularity."""
import json, random, sys
from datetime import datetime, timedelta, timezone
from fsrs import Scheduler, Card, Rating
from fsrs.scheduler import DEFAULT_PARAMETERS

EPOCH = datetime(2026, 1, 1, tzinfo=timezone.utc)
out_path = sys.argv[1]
cases = []
rng = random.Random(20261106)

def run_case(name, retention, plan):
    sch = Scheduler(desired_retention=retention, learning_steps=(), relearning_steps=(), enable_fuzzing=False)
    card = Card(card_id=1)
    day = 0
    steps = []
    for delay_kind, rating in plan:
        if steps:
            due_day = (card.due - EPOCH).days
            if delay_kind == "same":   day = day
            elif delay_kind == "due":  day = due_day
            elif delay_kind == "early": day = max(day + 1, due_day - max(1, (due_day - day) // 2))
            elif delay_kind == "late": day = due_day + max(1, (due_day - day))
        at = EPOCH + timedelta(days=day)
        r_before = None if card.last_review is None else sch.get_card_retrievability(card, current_datetime=at)
        card, _ = sch.review_card(card, Rating(rating), review_datetime=at)
        steps.append({
            "day": day, "rating": rating,
            "retrievabilityBefore": r_before,
            "stability": card.stability, "difficulty": card.difficulty,
            "dueDay": (card.due - EPOCH).days,
        })
    cases.append({"name": name, "desiredRetention": retention, "steps": steps})

# hand-written edge cases
run_case("first-review-each-rating-again", 0.9, [("due", 1)])
run_case("first-review-each-rating-hard", 0.9, [("due", 2)])
run_case("first-review-each-rating-good", 0.9, [("due", 3)])
run_case("first-review-each-rating-easy", 0.9, [("due", 4)])
run_case("all-good-on-due", 0.9, [("due", 3)] * 12)
run_case("all-easy-on-due", 0.9, [("due", 4)] * 10)
run_case("lapse-cycle", 0.9, [("due", 3), ("due", 3), ("due", 3), ("due", 1), ("due", 3), ("due", 3), ("due", 1), ("due", 1), ("due", 3)])
run_case("same-day-repeats", 0.9, [("due", 3), ("same", 3), ("same", 1), ("same", 4), ("due", 3)])
run_case("late-reviews", 0.9, [("due", 3), ("late", 3), ("late", 3), ("late", 1), ("late", 3)])
run_case("early-reviews", 0.9, [("due", 3), ("due", 3), ("early", 3), ("early", 2), ("early", 4)])
run_case("retention-0.8", 0.8, [("due", 3)] * 8)
run_case("retention-0.95", 0.95, [("due", 3)] * 8)
# random cases
kinds = ["due", "due", "due", "late", "early", "same"]
for i in range(40):
    n = rng.randint(3, 14)
    plan = [("due", rng.randint(1, 4))] + [(rng.choice(kinds), rng.choices([1, 2, 3, 4], weights=[2, 2, 5, 1])[0]) for _ in range(n - 1)]
    run_case(f"random-{i:02d}", rng.choice([0.85, 0.9, 0.9, 0.95]), plan)

json.dump({
    "generator": "py-fsrs 6.3.2 (Scheduler(learning_steps=(), relearning_steps=(), enable_fuzzing=False))",
    "parameters": list(DEFAULT_PARAMETERS),
    "notes": "day = whole days since an arbitrary epoch; ratings 1=Again 2=Hard 3=Good 4=Easy; retrievabilityBefore is null on the first review",
    "cases": cases,
}, open(out_path, "w"), indent=1)
print(f"{len(cases)} cases -> {out_path}")
