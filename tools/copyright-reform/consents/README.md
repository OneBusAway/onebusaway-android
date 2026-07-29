# Consent records

One file per copyright holder who has authorized relocating their notice from the
source-file headers to the repo `NOTICE` file (see `../CONSENT-REQUEST.md`).

Record each authorization here — e.g. `paul-watts.md` — with:

- the holder (and, for an organization, the authorizing person + role),
- the date and channel (email thread, GitHub issue link, signed statement),
- the affirmative text they gave, quoted, and
- which notices/patterns it covers.

Once a holder's consent is on file, remove their entry from `EXCLUDED_HOLDERS` in
`../apply-headers.py` and re-run the script so their files migrate.

Keep this as the durable audit trail; do not delete records after migrating.
