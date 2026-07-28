# Destination reminders

## Usage

1. Select a trip-plan itinerary, or long-press a destination in Trip Details.
2. Choose **Start reminders** and confirm the monitored transit rides.
3. Grant the requested location and notification permissions.
4. OneBusAway shows an ongoing notification while reminders are active.
5. The app announces when to get ready and when to exit at each transfer or final destination.

The ongoing notification can silence speech or stop the reminder session.

## How it works

`NavigationService` consumes location updates and passes each sample to the pure `ReminderEngine`.
The engine computes stop progression; the service presents notifications and text-to-speech messages.
Only the reminder plan and progression state needed for process restoration are persisted. The service
activates each monitored ride in order and stops after the final ride completes.

Location samples are processed ephemerally on the device. Destination reminders do not write
coordinates to files or the database, retain location traces, or upload rider location data. Builds
upgrading from the former trace-logging implementation delete its legacy `ObaNavLog` directory and
cancel its queued upload and cleanup work.

## Testing

Historical, anonymized CSV fixtures are bundled as test resources and replayed by JVM tests. They are
static regression inputs; the app no longer creates new recordings. Focused tests also cover noisy and
sparse fixes, poor accuracy, duplicate samples, process restoration, transfers, interlined legs, and
mode-aware wording.

## Feedback

After a trip, the app may ask whether the reminders arrived at the right time. A rider can submit a
thumbs-up or thumbs-down and optional text. This feedback contains no location trace or log attachment.
