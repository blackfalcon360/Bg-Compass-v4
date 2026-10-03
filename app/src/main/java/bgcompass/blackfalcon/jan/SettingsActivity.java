package bgcompass.blackfalcon.jan;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Pick your images, choose where the compass shows, place the text, and set the live wallpaper. */
public class SettingsActivity extends Activity {

    private static final int REQ_PICK_MAIN = 1;
    private static final int REQ_PICK_OTHER = 2;
    private static final int GOLD = 0xFFC9A227;
    private static final int POS_MIN = 40; // % of screen height

    private SharedPreferences prefs;
    private ImageView previewMain;
    private ImageView previewOther;
    private TextView posLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(CompassWallpaperService.PREFS, MODE_PRIVATE);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF101216);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setPadding(dp(24), dp(56), dp(24), dp(32));

        column.addView(label("BgCompass", 30, 0xFFFFFFFF, true));
        column.addView(label("Live compass wallpaper.\nDegrees and direction in one line (e.g. 245\u00B0 SW) just above the fingerprint.",
                15, 0xFF9AA0A6, false));

        // ---- compass image ----
        column.addView(label("Compass background image", 14, GOLD, false));
        previewMain = new ImageView(this);
        previewMain.setScaleType(ImageView.ScaleType.CENTER_CROP);
        previewMain.setBackgroundColor(0xFF1B1F27);
        column.addView(previewMain, new LinearLayout.LayoutParams(dp(150), dp(300)));
        column.addView(button("Choose background image", v -> pickImage(REQ_PICK_MAIN)), buttonParams());
        column.addView(button("Remove image", v -> removeImage(CompassWallpaperService.BG_FILE,
                CompassWallpaperService.KEY_BG_VERSION)), buttonParams());

        // ---- text position ----
        int pos = prefs.getInt(CompassWallpaperService.KEY_DIR_POS, CompassWallpaperService.DEFAULT_DIR_POS);
        posLabel = label("", 14, GOLD, false);
        posLabel.setPadding(0, dp(20), 0, dp(4));
        posLabel.setText("Text position: " + pos + "%  (just above your fingerprint)");
        column.addView(posLabel);

        SeekBar seek = new SeekBar(this);
        seek.setMax(90 - POS_MIN);
        seek.setProgress(pos - POS_MIN);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                int value = progress + POS_MIN;
                posLabel.setText("Text position: " + value + "%  (just above your fingerprint)");
                prefs.edit().putInt(CompassWallpaperService.KEY_DIR_POS, value).apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        column.addView(seek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- where should the compass show? ----
        TextView modeLabel = label("Show compass (image + text) on:", 14, GOLD, false);
        modeLabel.setPadding(0, dp(20), 0, dp(4));
        column.addView(modeLabel);

        RadioGroup modeGroup = new RadioGroup(this);
        modeGroup.setOrientation(RadioGroup.VERTICAL);
        String[] modeNames = {"Home & lock screen (both)", "Lock screen only", "Home screen only"};
        for (int m = 0; m < modeNames.length; m++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(1000 + m);
            rb.setText(modeNames[m]);
            rb.setTextColor(0xFFFFFFFF);
            modeGroup.addView(rb);
        }
        modeGroup.check(1000 + prefs.getInt(CompassWallpaperService.KEY_MODE, CompassWallpaperService.MODE_BOTH));
        modeGroup.setOnCheckedChangeListener((g, id) ->
                prefs.edit().putInt(CompassWallpaperService.KEY_MODE, id - 1000).apply());
        column.addView(modeGroup);

        TextView modeHint = label("Set the live wallpaper for \"Both\", then choose \"Lock screen only\": the compass image "
                + "and text show only on the lock screen. The other screen shows the image chosen below.",
                12, 0xFF9AA0A6, false);
        modeHint.setPadding(0, dp(8), 0, 0);
        column.addView(modeHint);

        // ---- image for the other screen ----
        TextView otherLabel = label("Image for the other screen", 14, GOLD, false);
        otherLabel.setPadding(0, dp(20), 0, dp(4));
        column.addView(otherLabel);
        TextView otherHint = label("Shown where the compass is off (e.g. your home screen when \"Lock screen only\" is on). "
                + "Choose your usual home wallpaper photo here. If empty, that screen is plain black.",
                12, 0xFF9AA0A6, false);
        otherHint.setPadding(0, 0, 0, dp(8));
        column.addView(otherHint);

        previewOther = new ImageView(this);
        previewOther.setScaleType(ImageView.ScaleType.CENTER_CROP);
        previewOther.setBackgroundColor(0xFF1B1F27);
        column.addView(previewOther, new LinearLayout.LayoutParams(dp(110), dp(220)));
        column.addView(button("Choose other-screen image", v -> pickImage(REQ_PICK_OTHER)), buttonParams());
        column.addView(button("Remove image", v -> removeImage(CompassWallpaperService.OTHER_FILE,
                CompassWallpaperService.KEY_OTHER_VERSION)), buttonParams());

        column.addView(button("Set as live wallpaper", v -> setWallpaper()), buttonParams());

        scroll.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView credit = label("By: Black Falcon", 14, GOLD, true);
        credit.setPadding(0, 0, 0, 0);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        lp.setMargins(0, dp(16), dp(20), 0);
        root.addView(credit, lp);

        setContentView(root);
        refreshPreviews();
    }

    private void pickImage(int requestCode) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        startActivityForResult(Intent.createChooser(i, "Choose image"), requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == REQ_PICK_MAIN) {
            importImage(data.getData(), CompassWallpaperService.BG_FILE, CompassWallpaperService.KEY_BG_VERSION);
        } else if (requestCode == REQ_PICK_OTHER) {
            importImage(data.getData(), CompassWallpaperService.OTHER_FILE, CompassWallpaperService.KEY_OTHER_VERSION);
        }
    }

    /** Copies the chosen image into the app (sized for this screen) so the wallpaper can use it. */
    private void importImage(final Uri uri, final String fileName, final String versionKey) {
        final DisplayMetrics dm = getResources().getDisplayMetrics();
        new Thread(() -> {
            boolean ok = false;
            try {
                int target = Math.max(dm.widthPixels, dm.heightPixels);

                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    BitmapFactory.decodeStream(in, null, bounds);
                }
                int sample = 1;
                while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) {
                    sample *= 2;
                }

                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = sample;
                Bitmap bmp;
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    bmp = BitmapFactory.decodeStream(in, null, opts);
                }

                if (bmp != null) {
                    int rotation = 0;
                    try (InputStream in = getContentResolver().openInputStream(uri)) {
                        ExifInterface exif = new ExifInterface(in);
                        int o = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                                ExifInterface.ORIENTATION_NORMAL);
                        if (o == ExifInterface.ORIENTATION_ROTATE_90) rotation = 90;
                        else if (o == ExifInterface.ORIENTATION_ROTATE_180) rotation = 180;
                        else if (o == ExifInterface.ORIENTATION_ROTATE_270) rotation = 270;
                    } catch (Exception ignored) {
                    }
                    if (rotation != 0) {
                        Matrix m = new Matrix();
                        m.postRotate(rotation);
                        Bitmap rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
                        bmp.recycle();
                        bmp = rotated;
                    }
                    try (FileOutputStream out = new FileOutputStream(new File(getFilesDir(), fileName))) {
                        ok = bmp.compress(Bitmap.CompressFormat.JPEG, 92, out);
                    }
                    bmp.recycle();
                }
            } catch (Throwable t) {
                ok = false;
            }
            final boolean done = ok;
            runOnUiThread(() -> {
                if (done) {
                    markChanged(versionKey);
                    Toast.makeText(this, "Image saved", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Could not load that image", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void removeImage(String fileName, String versionKey) {
        File f = new File(getFilesDir(), fileName);
        if (f.exists()) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
        markChanged(versionKey);
    }

    private void markChanged(String versionKey) {
        prefs.edit().putLong(versionKey, System.currentTimeMillis()).apply();
        refreshPreviews();
    }

    private void refreshPreviews() {
        showFile(previewMain, CompassWallpaperService.BG_FILE);
        showFile(previewOther, CompassWallpaperService.OTHER_FILE);
    }

    private void showFile(ImageView target, String fileName) {
        File f = new File(getFilesDir(), fileName);
        if (f.exists()) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 4;
            target.setImageBitmap(BitmapFactory.decodeFile(f.getPath(), o));
        } else {
            target.setImageDrawable(null);
        }
    }

    private void setWallpaper() {
        try {
            Intent i = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            i.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    new ComponentName(this, CompassWallpaperService.class));
            startActivity(i);
        } catch (Exception e) {
            try {
                startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));
            } catch (Exception e2) {
                Toast.makeText(this, "Open Settings > Wallpaper > Live wallpapers and pick BgCompass",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private Button button(String text, android.view.View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(l);
        return b;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        return lp;
    }

    private TextView label(String text, float sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, 0, 0, dp(16));
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
