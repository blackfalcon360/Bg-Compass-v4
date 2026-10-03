package bgcompass.blackfalcon.jan;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.hardware.SensorManager;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

import java.io.File;

/**
 * Live wallpaper: your image as background and one line of text just above the
 * fingerprint area: degrees + direction, e.g. "245° SW", in a single font size.
 *
 * "Show compass on" can be: home + lock screen (both), lock screen only, or home screen only.
 * The compass image + text appear only on the selected screen. The other screen shows the
 * separate "other screen" image (or plain black when none is chosen).
 */
public class CompassWallpaperService extends WallpaperService {

    static final String PREFS = "bgcompass_prefs";
    static final String KEY_BG_VERSION = "bg_version";
    static final String KEY_OTHER_VERSION = "other_version";
    static final String KEY_DIR_POS = "dir_pos";
    static final String KEY_MODE = "show_mode";
    static final String BG_FILE = "bg.jpg";
    static final String OTHER_FILE = "other_bg.jpg";
    static final int DEFAULT_DIR_POS = 74; // % of screen height

    static final int MODE_BOTH = 0;
    static final int MODE_LOCK = 1;
    static final int MODE_HOME = 2;

    private static final String[] DIRS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    @Override
    public Engine onCreateEngine() {
        return new CompassEngine();
    }

    private class CompassEngine extends Engine
            implements HeadingProvider.Listener, SharedPreferences.OnSharedPreferenceChangeListener {

        private HeadingProvider provider;
        private SharedPreferences prefs;
        private KeyguardManager keyguard;

        private int width, height;
        private boolean surfaceReady;
        private boolean visible;
        private boolean sensing;
        private Bitmap background;       // the compass image
        private Bitmap otherBackground;  // shown where the compass is switched off
        private boolean haveHeading;
        private float heading;
        private int lastDeg = -1;
        private int dirPos = DEFAULT_DIR_POS;
        private int mode = MODE_BOTH;

        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dimPaint = new Paint();

        // the phone was locked / unlocked / screen turned on or off -> re-check what to show
        private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                applyState();
            }
        };

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            dirPos = prefs.getInt(KEY_DIR_POS, DEFAULT_DIR_POS);
            mode = prefs.getInt(KEY_MODE, MODE_BOTH);
            prefs.registerOnSharedPreferenceChangeListener(this);

            keyguard = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_USER_PRESENT);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            registerReceiver(stateReceiver, filter);

            provider = new HeadingProvider(CompassWallpaperService.this, this, 0.2);

            textPaint.setColor(0xFFFFFFFF);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setShadowLayer(10f, 0f, 2f, 0xAA000000);

            dimPaint.setColor(0x55000000); // light dark overlay so text stays readable on any image
        }

        /** Is this screen one where the compass (image + text) should be shown right now? */
        private boolean compassScreen() {
            if (isPreview()) return true; // always show the compass in the wallpaper preview
            boolean locked = keyguard != null && keyguard.isKeyguardLocked();
            if (mode == MODE_LOCK) return locked;
            if (mode == MODE_HOME) return !locked;
            return true;
        }

        /** Starts/stops the sensors depending on what is on screen, then redraws. */
        private void applyState() {
            boolean need = visible && compassScreen();
            if (need && !sensing) {
                sensing = provider.start(SensorManager.SENSOR_DELAY_UI);
            } else if (!need && sensing) {
                provider.stop();
                sensing = false;
            }
            draw();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            super.onSurfaceChanged(holder, format, w, h);
            width = w;
            height = h;
            surfaceReady = true;
            loadBackgrounds();
            draw();
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            surfaceReady = false;
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onVisibilityChanged(boolean isVisible) {
            visible = isVisible;
            applyState(); // no sensor use while the wallpaper is not on screen
        }

        @Override
        public void onDestroy() {
            if (sensing) {
                provider.stop();
                sensing = false;
            }
            try {
                unregisterReceiver(stateReceiver);
            } catch (Exception ignored) {
            }
            prefs.unregisterOnSharedPreferenceChangeListener(this);
            recycleBackgrounds();
            super.onDestroy();
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
            if (KEY_BG_VERSION.equals(key) || KEY_OTHER_VERSION.equals(key)) {
                loadBackgrounds();
            } else if (KEY_DIR_POS.equals(key)) {
                dirPos = sp.getInt(KEY_DIR_POS, DEFAULT_DIR_POS);
            } else if (KEY_MODE.equals(key)) {
                mode = sp.getInt(KEY_MODE, MODE_BOTH);
            }
            applyState();
        }

        @Override
        public void onHeading(float degrees) {
            heading = degrees;
            haveHeading = true;
            int d = Math.round(degrees) % 360;
            if (d != lastDeg) { // redraw only when the visible number changes
                lastDeg = d;
                draw();
            }
        }

        private void recycleBackgrounds() {
            if (background != null) {
                background.recycle();
                background = null;
            }
            if (otherBackground != null) {
                otherBackground.recycle();
                otherBackground = null;
            }
        }

        private void loadBackgrounds() {
            recycleBackgrounds();
            if (width <= 0 || height <= 0) return;
            background = loadCover(new File(getFilesDir(), BG_FILE));
            otherBackground = loadCover(new File(getFilesDir(), OTHER_FILE));
        }

        /** Loads an image file and crops it to fill the screen. Returns null if there is none. */
        private Bitmap loadCover(File f) {
            if (!f.exists()) return null;
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(f.getPath(), bounds);
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

                int sample = 1;
                while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) {
                    sample *= 2;
                }
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = sample;
                Bitmap src = BitmapFactory.decodeFile(f.getPath(), opts);
                if (src == null) return null;

                Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(out);
                float scale = Math.max((float) width / src.getWidth(), (float) height / src.getHeight());
                Matrix m = new Matrix();
                m.postScale(scale, scale);
                m.postTranslate((width - src.getWidth() * scale) / 2f, (height - src.getHeight() * scale) / 2f);
                c.drawBitmap(src, m, new Paint(Paint.FILTER_BITMAP_FLAG));
                src.recycle();
                return out;
            } catch (Throwable t) {
                return null;
            }
        }

        private void draw() {
            if (!surfaceReady || width <= 0 || height <= 0) return;
            boolean compass = compassScreen();
            SurfaceHolder holder = getSurfaceHolder();
            Canvas c = null;
            try {
                c = holder.lockCanvas();
                if (c == null) return;

                Bitmap bmp = compass ? background : otherBackground;
                if (bmp != null) {
                    c.drawBitmap(bmp, 0f, 0f, null);
                    if (compass) c.drawRect(0f, 0f, width, height, dimPaint); // plain image on the other screen
                } else {
                    c.drawColor(compass ? 0xFF101216 : 0xFF000000);
                }

                if (compass) {
                    // one line, one font size: degrees + direction, just above the fingerprint
                    String line = haveHeading
                            ? (Math.round(heading) % 360) + "\u00B0 " + DIRS[((int) ((heading + 22.5f) / 45f)) % 8]
                            : "--\u00B0";
                    textPaint.setTextSize(width * 0.13f);
                    c.drawText(line, width / 2f, height * dirPos / 100f, textPaint);
                }
            } catch (Throwable ignored) {
                // never crash the wallpaper
            } finally {
                if (c != null) {
                    try {
                        holder.unlockCanvasAndPost(c);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
    }
}
