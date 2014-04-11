# Google Glass Progress Bar

As of now, Google Glass GDK API has no Progress Bar view. 
So I extracted this from GlassHome.apk (from https://github.com/zhuowei/Xenologer).

Progress & indeterminate slider:

<p align="center">
    <img src="http://i.imgur.com/5KsQKDs.gif">
</p>

Message dialog with success sound:

<p align="center">
    <img src="http://i.imgur.com/Ns1VP5O.gif">
</p>


## Features

1. __Progress bar__, which is used by all native apps.
2. Supports default progress.
3. Supports indeterminate progress.
4. Supports __MessageDialog with success sound__ found in Glass apps.


## Setup


1. Add this library project to your project.
2. (optional) override progress bar height in your own `res/values/dimens.xml` --> `<dimen name="slider_bar_height">12.0px</dimen>`
3. (optional) override message dialog background dimming in `res/values/dimens.xml` -->  `<item type="dimen" name="background_dim_amount">0.75</item>`

## Use it!
### For progress bar
1. Add progress bar view to your layout:
```xml
   <com.google.glass.widget.SliderView
       android:id="@+id/indeterm_slider"
       android:layout_width="fill_parent"
       android:layout_height="wrap_content"
       android:layout_alignParentBottom="true" />
```

2. Start it from activity:
```java
    mIndeterm.startIndeterminate();
    mProgress.startProgress(10 * 1000); // progress which lasts 10 seconds
```


### For Message Dialog
1. Use it in code:

```java
  	MessageDialog localDialog = new MessageDialog.Builder(ctx)
				.setTemporaryIcon(R.drawable.ic_sync_50)
				.setTemporaryMessage(R.string.dlg_temp_message)
				.setTemporarySecondaryMessage(R.string.dlg_temp_secondary_message)
				.setIcon(R.drawable.ic_done_50)
				.setMessage(R.string.dlg_message)
				.setSecondaryMessage(R.string.dlg_secondary_message)
				.setDismissable(true)
				.setAutoHide(true)
				.setListener(new MessageDialog.SimpleListener() {
					public boolean onConfirmed() {
						Log.d(TAG, "onConfirm");
						Toast.makeText(ctx, "onConfirmed", Toast.LENGTH_LONG).show();
						return true;
					}
					public void onDismissed() {
						Log.d(TAG, "onDismissed");
						Toast.makeText(ctx, "onDismissed", Toast.LENGTH_LONG).show();
					}
					public void onDone() {
						Log.d(TAG, "onDone");
						Toast.makeText(ctx, "onDone", Toast.LENGTH_LONG).show();
					}
				}).build();
		localDialog.show();
```

## Example

Look in `testwithlibrary` folder.
 
## Disclaimer

Google Glass is in Explorer Stage. That's why I tried to find the answer to this SO question http://stackoverflow.com/questions/20237873/google-glass-gdk-progress-indicator and a workaround for this issue https://code.google.com/p/google-glass-api/issues/detail?id=271

This is just for educational purposes and should not be used in any production apps until Google releases something similar officially.

I hope, Google will publish a set of Google Glass Views in the future releases of GDK. We really need them! Especially that awesome progress bar!
