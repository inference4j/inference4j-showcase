# Copyright 2026 the original author or authors. Licensed under the Apache License, Version 2.0.
#
# Renders the three phone calls of the RAG assistant's audio example with Kokoro TTS (booking LB-4471).
# Alice is af_heart (female). Shop staff: Emma bf_emma (female), Michael am_michael (male), George bm_george (male).
# Phone digits are spelled out word by word, because Moonshine misreads digit strings otherwise.
#
#   OMP_NUM_THREADS=4 uv run --no-project --with kokoro==0.9.4 --with soundfile python make_calls.py <outdir>
#   for f in <outdir>/*-24k.wav; do afconvert -f WAVE -d LEI16@16000 -c 1 "$f" "${f%-24k.wav}.wav"; done
#
# Copy the 16 kHz files to src/main/resources/rag/calls/.
import sys

import numpy as np
import soundfile as sf
from kokoro import KPipeline

out = sys.argv[1]
ALICE = ('a', 'af_heart')
EMMA = ('b', 'bf_emma')
MICHAEL = ('a', 'am_michael')
GEORGE = ('b', 'bm_george')
calls = {
 "call-2026-10-29": [
    (EMMA, "Lakeside Bike Rentals, Emma speaking."),
    (ALICE, "Hi, my name is Alice Martin. I booked an e-bike for this weekend, and I have a couple of questions."),
    (EMMA, "Of course. Go ahead."),
    (ALICE, "Is a helmet included, or do I need to bring my own?"),
    (EMMA, "A helmet and a lock are included with every rental, at no extra cost."),
    (ALICE, "Great. And how far can I go on one battery charge?"),
    (EMMA, "On a full charge the e-bike does about sixty kilometres. We also give you the charger, so you can top it up overnight."),
    (ALICE, "Perfect. What do I need to bring for pickup?"),
    (EMMA, "Just the card you paid with. Pickup opens at nine in the morning."),
    (ALICE, "Thank you, Emma. See you on Saturday."),
 ],
 "call-2026-11-02": [
    (MICHAEL, "Lakeside Bike Rentals, this is Michael speaking. How can I help?"),
    (ALICE, "Hi Michael, this is Alice Martin. I have the e-bike E fourteen, and it's due back at seven tonight."),
    (MICHAEL, "Sure, I have your booking here. What's going on?"),
    (ALICE, "The ferry back from the island is running an hour late, so the bike won't be back by seven."),
    (MICHAEL, "That's fine. Bring it back by eight, there will be no late fee."),
    (ALICE, "Thank you. Also, I fell on the gravel path this morning, and now the gears are grinding."),
    (MICHAEL, "Sorry to hear that. Are you hurt?"),
    (ALICE, "No, I'm fine. Just a scrape."),
    (MICHAEL, "Good. Take it easy on the gears, and we'll check the derailleur when you're back."),
    (ALICE, "Okay. If anything changes, you can call me back on four one five, five five five, zero one eight seven."),
    (MICHAEL, "Perfect. See you this evening."),
 ],
 "call-2026-11-04": [
    (GEORGE, "Lakeside Bike Rentals, this is George from the workshop."),
    (ALICE, "Hello George, it's Alice Martin. I returned an e-bike on Monday, and I'm calling about my deposit."),
    (GEORGE, "Let me have a look. Yes, part of your deposit was refunded on Monday evening."),
    (ALICE, "How long until the money is back on my card?"),
    (GEORGE, "Refunds take up to five business days to show on your card."),
    (ALICE, "Okay. I also think I left my sunglasses in the bike's basket."),
    (GEORGE, "We did find a pair of sunglasses. They are in the lost property box at the front desk, and we keep items there for thirty days."),
    (ALICE, "Wonderful, I will pick them up this week. Thanks, George."),
 ],
}
pipes = {}
for name, turns in calls.items():
    audio = [np.zeros(int(24000 * 0.4), dtype=np.float32)]
    for (lang, voice), text in turns:
        p = pipes.setdefault(lang, KPipeline(lang_code=lang, repo_id='hexgrad/Kokoro-82M'))
        for _, _, a in p(text, voice=voice, speed=1.0):
            audio.append(np.asarray(a))
        audio.append(np.zeros(int(24000 * 0.9), dtype=np.float32))
    sf.write(f"{out}/{name}-24k.wav", np.concatenate(audio), 24000)
    print("ok", name, sum(len(a) for a in audio) / 24000, "s")
