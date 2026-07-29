# Request to relocate your copyright notice to NOTICE

*Template for contacting each historical copyright holder in the OneBusAway for
Android codebase. Send one per holder (email or a tracked GitHub issue), filling in
the bracketed fields. File each reply — a plain affirmative "I authorize this" is
enough — under this directory (e.g. `consents/<holder>.md`) so the sign-off is
auditable. Do not run the header migration for a holder's notices until their
authorization is on file.*

---

**To:** [Holder name / representative]
**Re:** Consolidating copyright headers in OneBusAway for Android

Hi [name],

Our records show you (or an organization you represent) are named in one or more
copyright notices in the OneBusAway for Android source tree — specifically:

> [paste the exact notice(s) that name this holder, verbatim from NOTICE]

We're cleaning up the project's copyright headers. Over ~15 years the per-file
headers have drifted into 40+ inconsistent variants (differing years, name spellings,
and holder lists), which is a maintenance burden and is often inaccurate. We'd like to
adopt the convention used by the Apache Software Foundation, Kubernetes, and other
long-lived Apache-2.0 projects, and [recommended by the Linux Foundation][lf]:

- **Every source file** carries one identical Apache-2.0 license header that names no
  individual holder and no year:

  > Copyright The OneBusAway Authors.

- **All historical copyright notices** — including yours, exactly as written — move,
  verbatim, into the repository's top-level `NOTICE` file, which is distributed with
  the software and carried forward by anyone who redistributes it (Apache-2.0 §4(d)).

**What we're asking of you:** your authorization to *relocate* your copyright
notice(s) from the individual source-file headers to the `NOTICE` file.

**What this does NOT do:** it does **not** transfer, assign, or waive any of your
copyright. You retain all rights in your contributions exactly as before; only the
*location* of the notice changes, and its text is preserved verbatim. This is the
step Apache-2.0 §4 and the [ASF source-header policy][asf] contemplate for a
copyright owner moving their own notice into NOTICE. (Code we did not receive from
its owner — e.g. the Android Open Source Project files — is left untouched, notice
in place, per that same policy.)

If you're willing, please reply to this message with a clear affirmative, for example:

> I am a copyright holder of the notice(s) quoted above (or am authorized to act for
> the holder), and I authorize the OneBusAway project to relocate those notice(s)
> verbatim from the source-file headers to the project's NOTICE file. I understand
> this does not transfer or waive my copyright.

If you'd prefer we keep your notice in the source-file header instead, just say so and
we'll exclude your files from the change. If you believe the notice attributing you is
inaccurate (wrong years, should/shouldn't include you), let us know that too.

Thanks for your contributions to OneBusAway,
[Your name], on behalf of the Open Transit Software Foundation

[lf]: https://www.linuxfoundation.org/blog/blog/copyright-notices-in-open-source-software-projects
[asf]: https://www.apache.org/legal/src-headers.html

---

## Holders to contact

Derive the per-holder notice list from `NOTICE` (verbatim appendix). Known parties:

| Holder | Contact | Notes |
|---|---|---|
| Paul Watts | paulcwatts@gmail.com | Original author; named in `LICENSE` |
| University of South Florida | via Sean J. Barbeau | Institutional holder — USF may route through its tech-transfer / legal office |
| Sean J. Barbeau | sjbarbeau@gmail.com | Individual and USF-affiliated notices |
| Brian Ferris | bdferris@onebusaway.org | Original OneBusAway author |
| Microsoft Corporation | *needs a contact* | Corporate holder — likely needs OSPO/legal sign-off |
| Rodrigo Carvalho | carvalhorr@gmail.com | |
| Colin McDonough | *needs a contact* | |
| Cambridge Systematics, Inc. | *needs a contact* | Corporate holder — needs legal sign-off |
| Benjamin Du | bendu@me.com | |
| Open Transit Software Foundation | current steward | Self-authorizing; record for completeness |
| "individual contributors" (generic) | n/a | Catch-all text; no specific party to contact |

**Corporate holders (Microsoft, Cambridge Systematics) and an institutional one
(USF) are the long-pole items** — they typically route through a legal/OSPO process
rather than an individual reply. Start those first. If any holder cannot be reached
or declines, keep that holder's notice in-file (leave their entry in
`EXCLUDED_HOLDERS`) rather than relocating without authorization.

## How this drives the migration (gradual rollout)

The migration is not all-or-nothing. `tools/copyright-reform/apply-headers.py` holds
an `EXCLUDED_HOLDERS` list; a file is migrated to the canonical header only once
**none** of the holders it names are still excluded. The rollout:

1. **Now:** commit the tooling with every holder excluded. A run migrates only the
   files that name no holder at all (header-less files) — a safe no-op for attribution.
2. **First lift:** remove the `Open Transit Software Foundation` entry (the steward is
   self-authorizing). Re-run — the ~500 OTSF-only files migrate.
3. **As each authorization arrives:** record it under `consents/<holder>.md`, delete
   that holder's entry from `EXCLUDED_HOLDERS`, and re-run. Files blocked solely by
   that holder migrate; multi-holder files wait for their last holder. Already-migrated
   files are untouched (the rewrite is idempotent).

Run `apply-headers.py --dry-run` at any time to see the current per-holder blocked
counts, i.e. how many files each outstanding consent would unblock.

> Note: USF and Barbeau notices are intertwined (USF headers carry sjbarbeau@gmail.com),
> so lift the `University of South Florida` and `Sean J. Barbeau` entries together when
> that authorization arrives.

> **This is a human/legal sign-off gate, per the project's "no unsanctioned
> heuristics / human sign-off" rule.** An agent should not decide that the ICLA alone
> authorizes relocation and skip this step. Obtain and record consent, or retain the
> notice in-file.
