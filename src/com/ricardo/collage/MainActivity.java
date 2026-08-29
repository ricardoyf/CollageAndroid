package com.ricardo.collage;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.util.Size;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.text.DateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int REQ_TREE = 17;
    private static final String PREFS = "reencuadre_estado";
    private static final String SUFFIX_ENC = "_ENC";
    private static final String SUFFIX_COPY = "_COPIAde";
    private static final String BAD_SUFFIX = "_MALO.txt";
    private static final int MAX_BITMAP_SIDE = 4096;
    private static final int JPEG_QUALITY = 95;
    private static final int PRINT_DPI = 300;
    private static final String APP_VERSION = "v1.11";

    private final ArrayList<Photo> photos = new ArrayList<>();
    private final Set<String> processed = new HashSet<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private TextView info;
    private CropView cropView;
    private Button copyButton;
    private Button badButton;
    private Uri treeUri;
    private Uri rootDocUri;
    private String treeKey;
    private int index = 0;
    private int loadGeneration = 0;
    private boolean ratioLandscape = true;
    private float ratioWidth = 15f;
    private float ratioHeight = 10f;
    private String ratioLabel = "Libre";
    private boolean forceCopyForCurrentSave = false;
    private final ArrayList<Integer> history = new ArrayList<>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        Uri saved = getSavedTree();
        if (saved != null) {
            loadFolder(saved);
        } else {
            info.setText("Pulsa Carpeta y elige DCIM, Camera o la carpeta donde estén las fotos. Elige proporción antes de guardar.");
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(18, 18, 18));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        info = new TextView(this);
        info.setTextColor(Color.WHITE);
        info.setTextSize(15);
        info.setPadding(14, 10, 14, 8);
        header.addView(info, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1));

        TextView version = new TextView(this);
        version.setText(APP_VERSION);
        version.setTextColor(Color.rgb(156, 163, 175));
        version.setTextSize(11);
        version.setGravity(Gravity.RIGHT);
        version.setPadding(0, 0, dp(8), 0);
        header.addView(version, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        cropView = new CropView(this);
        root.addView(cropView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout controls = new LinearLayout(this);
        controls.setPadding(8, 8, 8, 12);
        controls.setOrientation(LinearLayout.VERTICAL);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER);
        bar.setOrientation(LinearLayout.HORIZONTAL);

        Button folder = button("Carpeta");
        Button choose = button("Fotos");
        Button marked = button("Marcadas");
        Button previous = button("Ant");
        Button skip = button("Saltar");

        LinearLayout rotateBar = new LinearLayout(this);
        rotateBar.setGravity(Gravity.CENTER);
        rotateBar.setPadding(0, 6, 0, 0);
        rotateBar.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout ratioBar = new LinearLayout(this);
        ratioBar.setGravity(Gravity.CENTER);
        ratioBar.setPadding(0, 6, 0, 0);
        ratioBar.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout actionBar = new LinearLayout(this);
        actionBar.setGravity(Gravity.CENTER);
        actionBar.setPadding(0, 6, 0, 0);
        actionBar.setOrientation(LinearLayout.HORIZONTAL);

        Button rotateLeft = button("-1°");
        Button reset = button("Reset");
        Button rotateRight = button("+1°");
        Button accept = button("Aceptar");
        Button share = button("Compartir");
        Button ratio1015 = button("10x15");
        Button ratio1318 = button("13x18");
        Button ratio1520 = button("15x20");
        Button ratio1824 = button("18x24");
        Button ratio2030 = button("20x30");
        Button ratioFree = button("Libre");
        Button ratioOrientation = button("H/V");
        fixedButton(ratio1015, 72);
        fixedButton(ratio1318, 72);
        fixedButton(ratio1520, 72);
        fixedButton(ratio1824, 72);
        fixedButton(ratio2030, 72);
        copyButton = button("COPIAde");
        badButton = button("Mala");

        folder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickFolder();
            }
        });
        choose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                choosePhoto(false);
            }
        });
        marked.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                choosePhoto(true);
            }
        });
        previous.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                previousPhoto();
            }
        });
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cropView.resetAll();
                updateInfo();
            }
        });
        rotateLeft.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cropView.rotateBy(-1f);
                updateInfo();
            }
        });
        rotateRight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cropView.rotateBy(1f);
                updateInfo();
            }
        });
        skip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                skip();
            }
        });
        accept.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                accept();
            }
        });
        share.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareCurrentCrop();
            }
        });
        ratio1015.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setPresetRatio(10f, 15f, "10x15");
            }
        });
        ratio1318.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setPresetRatio(13f, 18f, "13x18");
            }
        });
        ratio1520.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setPresetRatio(15f, 20f, "15x20");
            }
        });
        ratio1824.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setPresetRatio(18f, 24f, "18x24");
            }
        });
        ratio2030.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setPresetRatio(20f, 30f, "20x30");
            }
        });
        ratioFree.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setFreeRatio();
            }
        });
        ratioOrientation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleRatioOrientation();
            }
        });
        copyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!photos.isEmpty() && index < photos.size() && photos.get(index).hasCopy) {
                    unmarkCurrentCopy();
                } else {
                    markCurrentAsCopy();
                }
            }
        });
        badButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                markCurrentBadAndNext();
            }
        });

        bar.addView(folder);
        bar.addView(choose);
        bar.addView(marked);
        bar.addView(previous);
        bar.addView(skip);
        ratioBar.addView(ratio1015);
        ratioBar.addView(ratio1318);
        ratioBar.addView(ratio1520);
        ratioBar.addView(ratio1824);
        ratioBar.addView(ratio2030);
        actionBar.addView(ratioFree);
        actionBar.addView(ratioOrientation);
        actionBar.addView(copyButton);
        actionBar.addView(badButton);
        rotateBar.addView(rotateLeft);
        rotateBar.addView(reset);
        rotateBar.addView(rotateRight);
        rotateBar.addView(share);
        rotateBar.addView(accept);
        controls.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        controls.addView(ratioBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        controls.addView(actionBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        controls.addView(rotateBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(controls, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        setContentView(root);
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1);
        lp.setMargins(5, 0, 5, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void fixedButton(Button button, int widthDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                dp(widthDp),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(5, 0, 5, 0);
        button.setLayoutParams(lp);
    }

    private void tintButton(Button button, int color) {
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(color);
    }

    private void setPresetRatio(float width, float height, String label) {
        ratioWidth = width;
        ratioHeight = height;
        ratioLabel = label + (ratioLandscape ? " H" : " V");
        applyCurrentRatioToCropView();
        updateInfo();
    }

    private void setFreeRatio() {
        ratioLabel = "Libre";
        cropView.clearAspectRatio();
        updateInfo();
    }

    private void applyCurrentRatioToCropView() {
        if ("Libre".equals(ratioLabel)) {
            cropView.clearAspectRatio();
        } else {
            cropView.setAspectRatio(currentAspectRatio(), ratioLabel);
        }
    }

    private void toggleRatioOrientation() {
        ratioLandscape = !ratioLandscape;
        if (!"Libre".equals(ratioLabel)) {
            String base = ratioLabel.replace(" H", "").replace(" V", "");
            ratioLabel = base + (ratioLandscape ? " H" : " V");
            cropView.setAspectRatio(currentAspectRatio(), ratioLabel);
        }
        updateInfo();
    }

    private float currentAspectRatio() {
        float wide = Math.max(ratioWidth, ratioHeight);
        float tall = Math.min(ratioWidth, ratioHeight);
        return ratioLandscape ? wide / tall : tall / wide;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void pickFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, REQ_TREE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_TREE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            int flags = data.getFlags()
                    & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(uri, flags);
            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit()
                    .putString("last_tree", uri.toString())
                    .apply();
            loadFolder(uri);
        }
    }

    private Uri getSavedTree() {
        String value = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString("last_tree", null);
        if (value == null) return null;
        try {
            return Uri.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void loadFolder(Uri uri) {
        treeUri = uri;
        rootDocUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri));
        treeKey = "done_" + treeUri.toString();
        final int generation = ++loadGeneration;
        final Set<String> savedProcessed = new HashSet<>(getSharedPreferences(PREFS, MODE_PRIVATE)
                .getStringSet(treeKey, new HashSet<String>()));
        final Uri scanTreeUri = treeUri;
        final String folderDocId = DocumentsContract.getTreeDocumentId(treeUri);
        final String scanSuffix = currentOutputSuffix();
        cropView.setBitmap(null);
        photos.clear();
        processed.clear();
        processed.addAll(savedProcessed);
        history.clear();
        index = 0;
        info.setText("Leyendo carpeta Camera/fotos... puedes esperar unos segundos si hay muchas imágenes.");
        cropView.setAspectRatio(currentAspectRatio(), ratioLabel);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final ArrayList<Photo> found = new ArrayList<>();
                final Set<String> nextProcessed = new HashSet<>(savedProcessed);
                try {
                    scanFolder(scanTreeUri, folderDocId, "", found, nextProcessed, scanSuffix);
                    sortPhotosNewestFirst(found);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (generation != loadGeneration) return;
                            processed.clear();
                            processed.addAll(nextProcessed);
                            photos.clear();
                            photos.addAll(found);
                            history.clear();
                            index = 0;
                            saveProcessed();
                            showCurrent();
                        }
                    });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (generation != loadGeneration) return;
                            info.setText("No pude leer esa carpeta. Elige Camera o una carpeta normal de almacenamiento interno/SD.");
                            Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }, "collage-scan").start();
    }

    private void sortPhotosNewestFirst() {
        sortPhotosNewestFirst(photos);
    }

    private void sortPhotosNewestFirst(ArrayList<Photo> target) {
        Collections.sort(target, new Comparator<Photo>() {
            @Override
            public int compare(Photo a, Photo b) {
                int byDate = Long.compare(b.modified, a.modified);
                if (byDate != 0) return byDate;
                return a.key.compareToIgnoreCase(b.key);
            }
        });
    }

    private void scanFolder(
            Uri scanTreeUri,
            String folderDocId,
            String relativePath,
            ArrayList<Photo> outPhotos,
            Set<String> outProcessed,
            String scanSuffix) {
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                scanTreeUri,
                folderDocId);
        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
        };

        ArrayList<DocEntry> entries = new ArrayList<>();
        HashSet<String> namesInFolder = new HashSet<>();
        try (Cursor cursor = getContentResolver().query(
                childrenUri,
                projection,
                null,
                null,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME + " ASC")) {
            if (cursor == null) return;
            while (cursor.moveToNext()) {
                String docId = cursor.getString(0);
                String name = cursor.getString(1);
                String mime = cursor.getString(2);
                long modified = cursor.isNull(3) ? 0L : cursor.getLong(3);
                if (name == null || docId == null) continue;
                entries.add(new DocEntry(docId, name, mime, modified));
                namesInFolder.add(name);
            }
        }

        for (DocEntry entry : entries) {
            if (DocumentsContract.Document.MIME_TYPE_DIR.equals(entry.mime)) {
                scanFolder(scanTreeUri, entry.docId, relativePath + entry.name + "/", outPhotos, outProcessed, scanSuffix);
                continue;
            }

            if (!isSupportedPhoto(entry.name, entry.mime)) continue;
            String key = relativePath + entry.name;
            String cleanName = nameWithoutKnownSuffixes(entry.name);
            int cleanDot = cleanName.lastIndexOf('.');
            String cleanStem = cleanDot <= 0 ? cleanName : cleanName.substring(0, cleanDot);
            boolean hasBad = namesInFolder.contains(cleanStem + BAD_SUFFIX);

            boolean hasCopy = hasSuffixedVersion(entry.name, namesInFolder, SUFFIX_COPY);
            boolean hasEnc = hasSuffixedVersion(entry.name, namesInFolder, SUFFIX_ENC);
            String outName = outputEncName(entry.name, scanSuffix);
            if (namesInFolder.contains(outName)) {
                outProcessed.add(processedKey(key, scanSuffix));
                hasEnc = true;
            }
            if (hasBad) {
                outProcessed.add(processedKey(key, scanSuffix));
                continue;
            }
            if (hasCopy || hasEnc) {
                outProcessed.add(processedKey(key, scanSuffix));
                continue;
            }

            Uri docUri = DocumentsContract.buildDocumentUriUsingTree(scanTreeUri, entry.docId);
            Uri parentDocUri = DocumentsContract.buildDocumentUriUsingTree(
                    scanTreeUri,
                    folderDocId);
            outPhotos.add(new Photo(
                    key,
                    entry.name,
                    relativePath,
                    entry.modified,
                    entry.mime,
                    docUri,
                    childrenUri,
                    parentDocUri,
                    hasCopy,
                    hasEnc,
                    hasBad));
        }
    }

    private void choosePhoto(boolean onlyMarked) {
        if (photos.isEmpty()) return;
        final ArrayList<Integer> order = new ArrayList<>();
        for (int i = 0; i < photos.size(); i++) {
            Photo photo = photos.get(i);
            if (!onlyMarked || photo.hasCopy || photo.hasEnc || photo.hasBad) {
                order.add(i);
            }
        }
        if (order.isEmpty()) {
            Toast.makeText(this, "No hay fotos marcadas con COPIAde, ENC o MALO.", Toast.LENGTH_LONG).show();
            return;
        }
        Collections.sort(order, new Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                Photo pa = photos.get(a);
                Photo pb = photos.get(b);
                int byDate = Long.compare(pb.modified, pa.modified);
                if (byDate != 0) return byDate;
                return pa.key.compareToIgnoreCase(pb.key);
            }
        });
        DateFormat format = DateFormat.getDateTimeInstance(
                DateFormat.SHORT,
                DateFormat.SHORT,
                Locale.getDefault());
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int rowPad = dp(8);
        final AlertDialog[] dialogRef = new AlertDialog[1];
        for (int i = 0; i < order.size(); i++) {
            final int photoIndex = order.get(i);
            Photo p = photos.get(photoIndex);
            String date = p.modified > 0 ? format.format(new Date(p.modified)) : "sin fecha";
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(rowPad, rowPad, rowPad, rowPad);

            ImageView thumb = new ImageView(this);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Bitmap thumbnail = loadThumbnail(p);
            if (thumbnail != null) {
                thumb.setImageBitmap(thumbnail);
            } else {
                thumb.setBackgroundColor(Color.rgb(70, 70, 70));
            }
            row.addView(thumb, new LinearLayout.LayoutParams(dp(72), dp(72)));

            TextView label = new TextView(this);
            String marks = marksForPhoto(p);
            label.setText((marks.length() > 0 ? "✓ " + marks + "\n" : "")
                    + date + "\n" + p.key);
            label.setTextColor(Color.rgb(30, 30, 30));
            label.setTextSize(14);
            label.setPadding(dp(10), 0, 0, 0);
            row.addView(label, new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1));
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    index = photoIndex;
                    showCurrent();
                    if (dialogRef[0] != null) dialogRef[0].dismiss();
                }
            });
            row.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    showFullScreenPhoto(photos.get(photoIndex));
                    return true;
                }
            });
            list.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        scroll.addView(list);
        dialogRef[0] = new AlertDialog.Builder(this)
                .setTitle(onlyMarked ? "Fotos marcadas · mantén pulsada para pantalla completa" : "Elegir foto · mantén pulsada para pantalla completa")
                .setView(scroll)
                .create();
        dialogRef[0].show();
    }

    private String marksForPhoto(Photo photo) {
        ArrayList<String> marks = new ArrayList<>();
        if (photo.hasCopy) marks.add("COPIAde");
        if (photo.hasEnc) marks.add("ENC");
        if (photo.hasBad) marks.add("MALO");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < marks.size(); i++) {
            if (i > 0) builder.append(" + ");
            builder.append(marks.get(i));
        }
        return builder.toString();
    }

    private void showFullScreenPhoto(Photo photo) {
        try {
            ImageView view = new ImageView(this);
            view.setBackgroundColor(Color.BLACK);
            view.setScaleType(ImageView.ScaleType.FIT_CENTER);
            view.setAdjustViewBounds(true);
            Bitmap bitmap = loadBitmapRespectingOrientation(photo.uri);
            view.setImageBitmap(bitmap);
            new AlertDialog.Builder(this)
                    .setTitle(photo.name)
                    .setView(view)
                    .setPositiveButton("Cerrar", null)
                    .show();
        } catch (Exception e) {
            Toast.makeText(this, "No pude abrir vista completa.", Toast.LENGTH_LONG).show();
        }
    }

    private Bitmap loadThumbnail(Photo photo) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                return getContentResolver().loadThumbnail(
                        photo.uri,
                        new Size(dp(96), dp(96)),
                        null);
            }
        } catch (Exception ignored) {
        }
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(photo.uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            int sample = 1;
            int maxSide = Math.max(bounds.outWidth, bounds.outHeight);
            while (maxSide / sample > 192) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap bitmap;
            try (InputStream in = getContentResolver().openInputStream(photo.uri)) {
                bitmap = BitmapFactory.decodeStream(in, null, opts);
            }
            if (bitmap == null) return null;
            return rotateThumbnailForExif(bitmap, photo.uri);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Bitmap rotateThumbnailForExif(Bitmap bitmap, Uri uri) {
        int orientation = ExifInterface.ORIENTATION_NORMAL;
        try (InputStream exifIn = getContentResolver().openInputStream(uri)) {
            if (exifIn != null) {
                ExifInterface exif = new ExifInterface(exifIn);
                orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL);
            }
        } catch (Exception ignored) {
            return bitmap;
        }
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(270);
                break;
            default:
                return bitmap;
        }
        Bitmap rotated = Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.getWidth(),
                bitmap.getHeight(),
                matrix,
                true);
        if (rotated != bitmap) bitmap.recycle();
        return rotated;
    }

    private void previousPhoto() {
        if (photos.isEmpty()) return;
        if (!history.isEmpty()) {
            index = history.remove(history.size() - 1);
            showCurrent();
            return;
        }
        if (index <= 0) {
            Toast.makeText(this, "Ya estas en la primera foto.", Toast.LENGTH_SHORT).show();
            return;
        }
        index--;
        showCurrent();
    }

    private boolean isSupportedPhoto(String name, String mime) {
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("_collage") || lower.contains("_enc") || lower.contains("_copiade")) return false;
        if (mime != null && mime.startsWith("image/")) return true;
        return lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".png")
                || lower.endsWith(".webp")
                || lower.endsWith(".bmp");
    }

    private void showCurrent() {
        if (photos.isEmpty() || index >= photos.size()) {
            cropView.setBitmap(null);
            info.setText("No quedan fotos pendientes. La app ahora revisa también subcarpetas. Si falta algo, prueba con DCIM o Camera.");
            return;
        }

        Photo p = photos.get(index);
        try {
            Bitmap bitmap = loadBitmapRespectingOrientation(p.uri);
            if (bitmap == null) {
                throw new IllegalStateException("Imagen no compatible");
            }
            cropView.setBitmap(bitmap);
            updateCopySwitch();
            updateBadSwitch(p.hasBad);
            updateInfo();
        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "No pude abrir " + p.name,
                    Toast.LENGTH_SHORT).show();
            index++;
            showCurrent();
        }
    }

    private void updateInfo() {
        if (photos.isEmpty() || index >= photos.size()) return;
        Photo p = photos.get(index);
        info.setText((index + 1) + "/" + photos.size() + " - " + p.key
                + " | Restantes: " + (photos.size() - index)
                + " | Proporción: " + cropView.aspectLabel()
                + " | Marcas: " + (marksForPhoto(p).length() > 0 ? marksForPhoto(p) : "sin marca")
                + " | Giro: " + cropView.rotationLabel());
    }

    private void updateCopySwitch() {
        if (copyButton == null) return;
        boolean checked = !photos.isEmpty()
                && index < photos.size()
                && photos.get(index).hasCopy;
        copyButton.setSelected(checked);
        copyButton.setText(checked ? "Quitar copia" : "COPIAde");
        tintButton(copyButton, checked ? Color.rgb(22, 101, 52) : Color.rgb(55, 65, 81));
    }

    private void updateBadSwitch(boolean checked) {
        if (badButton == null) return;
        badButton.setSelected(checked);
        tintButton(badButton, checked ? Color.rgb(185, 28, 28) : Color.rgb(55, 65, 81));
    }

    private Bitmap loadBitmapRespectingOrientation(Uri uri) throws Exception {
        Bitmap bitmap;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sampleSizeForMaxSide(
                Math.max(bounds.outWidth, bounds.outHeight),
                MAX_BITMAP_SIDE);
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            bitmap = BitmapFactory.decodeStream(in, null, opts);
        }
        if (bitmap == null) return null;

        int orientation = ExifInterface.ORIENTATION_NORMAL;
        try (InputStream exifIn = getContentResolver().openInputStream(uri)) {
            if (exifIn != null) {
                ExifInterface exif = new ExifInterface(exifIn);
                orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL);
            }
        } catch (Exception ignored) {
            orientation = ExifInterface.ORIENTATION_NORMAL;
        }

        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setScale(1, -1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(270);
                break;
            case ExifInterface.ORIENTATION_NORMAL:
            case ExifInterface.ORIENTATION_UNDEFINED:
            default:
                return bitmap;
        }

        Bitmap rotated = Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.getWidth(),
                bitmap.getHeight(),
                matrix,
                true);
        if (rotated != bitmap) {
            bitmap.recycle();
        }
        return rotated;
    }

    private int sampleSizeForMaxSide(int maxSide, int limit) {
        int sample = 1;
        while (maxSide / sample > limit) {
            sample *= 2;
        }
        return sample;
    }

    private void skip() {
        if (photos.isEmpty()) return;
        pushHistory();
        index++;
        showCurrent();
    }

    private void pushHistory() {
        if (!photos.isEmpty() && index >= 0 && index < photos.size()) {
            history.add(index);
        }
    }

    private void accept() {
        if (photos.isEmpty()
                || index >= photos.size()
                || cropView.bitmap == null) {
            return;
        }
        try {
            saveCurrentCropAndAdvance(false);
        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "Error al guardar: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void shareCurrentCrop() {
        if (photos.isEmpty()
                || index >= photos.size()
                || cropView.bitmap == null) {
            return;
        }
        try {
            SaveResult result = saveCurrentCropAndAdvance(true);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("image/jpeg");
            share.putExtra(Intent.EXTRA_STREAM, result.uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Compartir foto reencuadrada"));
        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "Error al compartir: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private SaveResult saveCurrentCropAndAdvance(boolean sharing) throws Exception {
        Photo p = photos.get(index);
        Bitmap cropped = cropView.makeCrop();
        if (cropped == null) throw new IllegalStateException("Recorte no válido");
        Bitmap output = preparePrintBitmap(cropped);
        if (output != cropped) cropped.recycle();

        String outName = outputEncName(p.name);
        deleteIfExists(outName);
        Uri out = DocumentsContract.createDocument(
                getContentResolver(),
                p.parentDocUri,
                "image/jpeg",
                outName);
        if (out == null) {
            throw new IllegalStateException("Android no creó el archivo de salida");
        }
        writeJpegWithPrintMetadata(output, out);
        if (output != cropView.bitmap) output.recycle();

        if (forceCopyForCurrentSave) {
            p.hasCopy = true;
        }
        p.hasEnc = true;
        if (p.hasBad) {
            deleteBadMarker(p);
            p.hasBad = false;
        }
        processed.add(p.key);
        processed.add(processedKey(p.key));
        saveProcessed();
        pushHistory();
        index++;
        showCurrent();
        if (!sharing) {
            Toast.makeText(this, "Guardado JPEG 95", Toast.LENGTH_SHORT).show();
        }
        return new SaveResult(out, outName);
    }

    private Bitmap preparePrintBitmap(Bitmap cropped) {
        Bitmap rgb = Bitmap.createBitmap(
                cropped.getWidth(),
                cropped.getHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(rgb);
        canvas.drawColor(Color.WHITE);
        canvas.drawBitmap(cropped, 0, 0, null);

        int[] target = targetPixelsForCurrentRatio();
        if (target == null) return rgb;
        int targetW = target[0];
        int targetH = target[1];
        if (rgb.getWidth() > targetW && rgb.getHeight() > targetH) {
            Bitmap resized = Bitmap.createScaledBitmap(rgb, targetW, targetH, true);
            if (resized != rgb) rgb.recycle();
            return resized;
        }
        return rgb;
    }

    private int[] targetPixelsForCurrentRatio() {
        if ("Libre".equals(ratioLabel)) return null;
        float shortCm = Math.min(ratioWidth, ratioHeight);
        float longCm = Math.max(ratioWidth, ratioHeight);
        int shortPx = Math.round((shortCm / 2.54f) * PRINT_DPI);
        int longPx = Math.round((longCm / 2.54f) * PRINT_DPI);
        return ratioLandscape
                ? new int[] { longPx, shortPx }
                : new int[] { shortPx, longPx };
    }

    private void writeJpegWithPrintMetadata(Bitmap bitmap, Uri out) throws Exception {
        File temp = File.createTempFile("collage-enc-", ".jpg", getCacheDir());
        try {
            try (FileOutputStream fos = new FileOutputStream(temp)) {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fos)) {
                    throw new IllegalStateException("No pude comprimir JPEG");
                }
            }
            ExifInterface exif = new ExifInterface(temp.getAbsolutePath());
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(ExifInterface.ORIENTATION_NORMAL));
            exif.setAttribute(ExifInterface.TAG_X_RESOLUTION, PRINT_DPI + "/1");
            exif.setAttribute(ExifInterface.TAG_Y_RESOLUTION, PRINT_DPI + "/1");
            exif.setAttribute(ExifInterface.TAG_RESOLUTION_UNIT, "2");
            exif.setAttribute(ExifInterface.TAG_COLOR_SPACE, "1");
            exif.saveAttributes();
            try (InputStream in = new java.io.FileInputStream(temp);
                 OutputStream os = getContentResolver().openOutputStream(out, "wt")) {
                if (os == null) throw new IllegalStateException("No pude escribir JPEG");
                byte[] buffer = new byte[1024 * 64];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
            }
        } finally {
            temp.delete();
        }
    }

    private void deleteIfExists(String displayName) {
        if (photos.isEmpty() || index >= photos.size()) return;
        deleteIfExistsForPhoto(photos.get(index), displayName);
    }

    private void deleteIfExistsForPhoto(Photo photo, String displayName) {
        Uri childrenUri = photo.childrenUri;
        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
        };
        try (Cursor cursor = getContentResolver().query(
                childrenUri,
                projection,
                null,
                null,
                null)) {
            if (cursor == null) return;
            while (cursor.moveToNext()) {
                if (displayName.equals(cursor.getString(1))) {
                    Uri docUri = DocumentsContract.buildDocumentUriUsingTree(
                            treeUri,
                            cursor.getString(0));
                    DocumentsContract.deleteDocument(
                            getContentResolver(),
                            docUri);
                    return;
                }
            }
        } catch (Exception ignored) {
        }
    }

    private int deleteSuffixedVersions(Photo photo, String suffix) throws Exception {
        int removed = 0;
        String clean = nameWithoutKnownSuffixes(photo.name);
        int dot = clean.lastIndexOf('.');
        String stem = dot <= 0 ? clean : clean.substring(0, dot);
        String expectedStart = (stem + suffix).toLowerCase(Locale.ROOT);
        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
        };
        try (Cursor cursor = getContentResolver().query(
                photo.childrenUri,
                projection,
                null,
                null,
                null)) {
            if (cursor == null) return 0;
            while (cursor.moveToNext()) {
                String displayName = cursor.getString(1);
                if (displayName == null) continue;
                if (displayName.toLowerCase(Locale.ROOT).startsWith(expectedStart)) {
                    Uri docUri = DocumentsContract.buildDocumentUriUsingTree(
                            treeUri,
                            cursor.getString(0));
                    if (DocumentsContract.deleteDocument(getContentResolver(), docUri)) {
                        removed++;
                    }
                }
            }
        }
        return removed;
    }

    private boolean deleteBadMarker(Photo photo) {
        String clean = nameWithoutKnownSuffixes(photo.name);
        int dot = clean.lastIndexOf('.');
        String stem = dot <= 0 ? clean : clean.substring(0, dot);
        String markerName = stem + BAD_SUFFIX;
        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
        };
        try (Cursor cursor = getContentResolver().query(
                photo.childrenUri,
                projection,
                null,
                null,
                null)) {
            if (cursor == null) return false;
            while (cursor.moveToNext()) {
                if (markerName.equals(cursor.getString(1))) {
                    Uri docUri = DocumentsContract.buildDocumentUriUsingTree(
                            treeUri,
                            cursor.getString(0));
                    return DocumentsContract.deleteDocument(getContentResolver(), docUri);
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void markCurrentAsCopy() {
        if (photos.isEmpty() || index >= photos.size()) {
            updateCopySwitch();
            return;
        }
        Photo p = photos.get(index);
        try {
            forceCopyForCurrentSave = true;
            saveCurrentCropAndAdvance(false);
        } catch (Exception e) {
            Toast.makeText(this, "No pude crear COPIAde_ENC: " + e.getMessage(), Toast.LENGTH_LONG).show();
            updateCopySwitch();
        } finally {
            forceCopyForCurrentSave = false;
        }
    }

    private void unmarkCurrentCopy() {
        if (photos.isEmpty() || index >= photos.size()) {
            updateCopySwitch();
            return;
        }
        Photo p = photos.get(index);
        try {
            int removed = deleteSuffixedVersions(p, SUFFIX_COPY);
            p.hasCopy = false;
            updateCopySwitch();
            updateInfo();
            Toast.makeText(
                    this,
                    removed > 0 ? "Quitado COPIAde" : "No habia COPIAde que borrar.",
                    Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "No pude quitar COPIAde: " + e.getMessage(), Toast.LENGTH_LONG).show();
            updateCopySwitch();
        }
    }

    private void markCurrentBadAndNext() {
        if (photos.isEmpty() || index >= photos.size()) {
            updateBadSwitch(false);
            return;
        }
        final Photo p = photos.get(index);
        p.hasBad = true;
        p.hasCopy = false;
        p.hasEnc = false;
        processed.add(p.key);
        processed.add(processedKey(p.key));
        saveProcessed();
        pushHistory();
        index++;
        showCurrent();
        Toast.makeText(this, "Marcando MALA", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                markBadOnDisk(p);
            }
        }, "collage-mark-bad").start();
    }

    private void markBadOnDisk(Photo p) {
        String clean = nameWithoutKnownSuffixes(p.name);
        int dot = clean.lastIndexOf('.');
        String stem = dot <= 0 ? clean : clean.substring(0, dot);
        String markerName = stem + BAD_SUFFIX;
        try {
            deleteSuffixedVersions(p, SUFFIX_COPY);
            deleteSuffixedVersions(p, SUFFIX_ENC);
            deleteIfExistsForPhoto(p, markerName);
            Uri marker = DocumentsContract.createDocument(
                    getContentResolver(),
                    p.parentDocUri,
                    "text/plain",
                    markerName);
            if (marker == null) throw new IllegalStateException("Android no creó marcador MALO");
            try (OutputStream os = getContentResolver().openOutputStream(marker, "wt")) {
                if (os == null) throw new IllegalStateException("No pude escribir marcador");
                os.write(("MALO\n" + p.name + "\n").getBytes("UTF-8"));
            }
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "MALA guardada", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (final Exception e) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "No pude marcar MALA: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private String outputName(String name) {
        return outputName(nameWithoutKnownSuffixes(name), currentOutputSuffix());
    }

    private String outputEncName(String name) {
        return outputEncName(nameWithoutKnownSuffixes(name), currentOutputSuffix());
    }

    private String outputEncName(String name, String suffix) {
        int dot = name.lastIndexOf('.');
        String stem = dot <= 0 ? name : name.substring(0, dot);
        return stem + suffix + ".jpg";
    }

    private String outputName(String name, String suffix) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0) return name + suffix + ".jpg";
        return name.substring(0, dot) + suffix + name.substring(dot);
    }

    private String currentOutputSuffix() {
        String copy = forceCopyForCurrentSave ? SUFFIX_COPY : "";
        if ("Libre".equals(ratioLabel)) return copy + SUFFIX_ENC + "_LIBRE";
        return copy + SUFFIX_ENC + "_" + ratioLabel.replace(" ", "");
    }

    private String processedKey(String key) {
        return processedKey(key, currentOutputSuffix());
    }

    private String processedKey(String key, String suffix) {
        return suffix + ":" + key;
    }

    private String nameWithoutKnownSuffixes(String name) {
        int dot = name.lastIndexOf('.');
        String stem = dot <= 0 ? name : name.substring(0, dot);
        String ext = dot <= 0 ? "" : name.substring(dot);
        stem = stem.replaceAll("(?i)_COLLAGE(?:_[0-9]+x[0-9]+[HV]|_LIBRE)?$", "");
        stem = stem.replaceAll("(?i)_COPIAde(?:_ENC(?:_[0-9]+x[0-9]+[HV]|_LIBRE)?)?$", "");
        stem = stem.replaceAll("(?i)_ENC(?:_[0-9]+x[0-9]+[HV]|_LIBRE)?$", "");
        return stem + ext;
    }

    private boolean hasSuffixedVersion(String name, Set<String> namesInFolder, String suffix) {
        String clean = nameWithoutKnownSuffixes(name);
        int dot = clean.lastIndexOf('.');
        String stem = dot <= 0 ? clean : clean.substring(0, dot);
        String ext = dot <= 0 ? "" : clean.substring(dot);
        String expectedStart = (stem + suffix).toLowerCase(Locale.ROOT);
        for (String candidate : namesInFolder) {
            String lower = candidate.toLowerCase(Locale.ROOT);
            boolean expectedExt = suffix.equals(SUFFIX_ENC)
                    ? isSupportedImageExtension(lower)
                    : lower.endsWith(ext.toLowerCase(Locale.ROOT));
            if (lower.startsWith(expectedStart) && expectedExt) {
                return true;
            }
        }
        return false;
    }

    private boolean isSupportedImageExtension(String lowerName) {
        return lowerName.endsWith(".jpg")
                || lowerName.endsWith(".jpeg")
                || lowerName.endsWith(".png")
                || lowerName.endsWith(".webp")
                || lowerName.endsWith(".bmp");
    }

    private String outputMime(String name, String fallback) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (fallback != null && fallback.startsWith("image/")) return fallback;
        return "image/jpeg";
    }

    private void saveProcessed() {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putStringSet(treeKey, new HashSet<String>(processed))
                .apply();
    }

    private static class Photo {
        final String key;
        final String name;
        final String relativePath;
        final long modified;
        final String mime;
        final Uri uri;
        final Uri childrenUri;
        final Uri parentDocUri;
        boolean hasCopy;
        boolean hasEnc;
        boolean hasBad;

        Photo(
                String key,
                String name,
                String relativePath,
                long modified,
                String mime,
                Uri uri,
                Uri childrenUri,
                Uri parentDocUri,
                boolean hasCopy,
                boolean hasEnc,
                boolean hasBad) {
            this.key = key;
            this.name = name;
            this.relativePath = relativePath;
            this.modified = modified;
            this.mime = mime;
            this.uri = uri;
            this.childrenUri = childrenUri;
            this.parentDocUri = parentDocUri;
            this.hasCopy = hasCopy;
            this.hasEnc = hasEnc;
            this.hasBad = hasBad;
        }
    }

    private static class DocEntry {
        final String docId;
        final String name;
        final String mime;
        final long modified;

        DocEntry(String docId, String name, String mime, long modified) {
            this.docId = docId;
            this.name = name;
            this.mime = mime;
            this.modified = modified;
        }
    }

    private static class SaveResult {
        final Uri uri;
        final String name;

        SaveResult(Uri uri, String name) {
            this.uri = uri;
            this.name = name;
        }
    }

    public static class CropView extends View {
        Bitmap bitmap;
        private Bitmap originalBitmap;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF imageRect = new RectF();
        private final RectF crop = new RectF();
        private int mode = 0;
        private float lastX;
        private float lastY;
        private float rotationDegrees = 0f;
        private float lockedAspectRatio = 15f / 10f;
        private String lockedAspectLabel = "10x15 H";

        public CropView(android.content.Context context) {
            super(context);
            dim.setColor(Color.argb(135, 0, 0, 0));
            setBackgroundColor(Color.rgb(18, 18, 18));
        }

        void setBitmap(Bitmap b) {
            recycleWorkingBitmap();
            if (originalBitmap != null && originalBitmap != b) {
                originalBitmap.recycle();
            }
            originalBitmap = b;
            rotationDegrees = 0f;
            rebuildWorkingBitmap();
            resetCrop();
        }

        void resetCrop() {
            post(new Runnable() {
                @Override
                public void run() {
                    calculateImageRect();
                    if (!imageRect.isEmpty()) {
                        float mx = imageRect.width() * 0.08f;
                        float my = imageRect.height() * 0.08f;
                        RectF available = new RectF(
                                imageRect.left + mx,
                                imageRect.top + my,
                                imageRect.right - mx,
                                imageRect.bottom - my);
                        if (lockedAspectRatio > 0f) {
                            fitAspectCrop(available);
                        } else {
                            crop.set(available);
                        }
                    } else {
                        crop.setEmpty();
                    }
                    invalidate();
                }
            });
        }

        void resetAll() {
            rotationDegrees = 0f;
            rebuildWorkingBitmap();
            resetCrop();
        }

        void setAspectRatio(float ratio, String label) {
            lockedAspectRatio = ratio > 0f ? ratio : 0f;
            lockedAspectLabel = label;
            resetCrop();
        }

        void clearAspectRatio() {
            lockedAspectRatio = 0f;
            lockedAspectLabel = "Libre";
            resetCrop();
        }

        String aspectLabel() {
            return lockedAspectLabel;
        }

        void rotateBy(float degrees) {
            if (originalBitmap == null) return;
            RectF normalizedCrop = normalizedCrop();
            rotationDegrees += degrees;
            if (rotationDegrees > 45f) rotationDegrees = 45f;
            if (rotationDegrees < -45f) rotationDegrees = -45f;
            rebuildWorkingBitmap();
            restoreNormalizedCrop(normalizedCrop);
        }

        String rotationLabel() {
            return String.format(Locale.US, "%.0f°", rotationDegrees);
        }

        private void rebuildWorkingBitmap() {
            if (originalBitmap == null) {
                bitmap = null;
                return;
            }
            recycleWorkingBitmap();
            if (Math.abs(rotationDegrees) < 0.01f) {
                bitmap = originalBitmap;
                return;
            }
            Matrix matrix = new Matrix();
            matrix.setRotate(rotationDegrees);
            bitmap = Bitmap.createBitmap(
                    originalBitmap,
                    0,
                    0,
                    originalBitmap.getWidth(),
                    originalBitmap.getHeight(),
                    matrix,
                    true);
        }

        private void recycleWorkingBitmap() {
            if (bitmap != null && bitmap != originalBitmap) {
                bitmap.recycle();
            }
            bitmap = null;
        }

        private void calculateImageRect() {
            if (bitmap == null || getWidth() == 0 || getHeight() == 0) {
                imageRect.setEmpty();
                return;
            }
            float scale = Math.min(
                    getWidth() / (float) bitmap.getWidth(),
                    getHeight() / (float) bitmap.getHeight());
            float w = bitmap.getWidth() * scale;
            float h = bitmap.getHeight() * scale;
            float left = (getWidth() - w) / 2f;
            float top = (getHeight() - h) / 2f;
            imageRect.set(left, top, left + w, top + h);
        }

        private RectF normalizedCrop() {
            calculateImageRect();
            if (imageRect.isEmpty() || crop.isEmpty()) return null;
            return new RectF(
                    (crop.left - imageRect.left) / imageRect.width(),
                    (crop.top - imageRect.top) / imageRect.height(),
                    (crop.right - imageRect.left) / imageRect.width(),
                    (crop.bottom - imageRect.top) / imageRect.height());
        }

        private void restoreNormalizedCrop(final RectF normalized) {
            post(new Runnable() {
                @Override
                public void run() {
                    calculateImageRect();
                    if (normalized == null || imageRect.isEmpty()) {
                        resetCrop();
                        return;
                    }
                    crop.set(
                            imageRect.left + normalized.left * imageRect.width(),
                            imageRect.top + normalized.top * imageRect.height(),
                            imageRect.left + normalized.right * imageRect.width(),
                            imageRect.top + normalized.bottom * imageRect.height());
                    keepCropInsideImage();
                    invalidate();
                }
            });
        }

        private void keepCropInsideImage() {
            if (imageRect.isEmpty() || crop.isEmpty()) return;
            float min = 60f;
            if (crop.width() < min || crop.height() < min) {
                resetCrop();
                return;
            }
            if (crop.width() > imageRect.width()) {
                crop.left = imageRect.left;
                crop.right = imageRect.right;
            }
            if (crop.height() > imageRect.height()) {
                crop.top = imageRect.top;
                crop.bottom = imageRect.bottom;
            }
            if (crop.left < imageRect.left) crop.offset(imageRect.left - crop.left, 0);
            if (crop.top < imageRect.top) crop.offset(0, imageRect.top - crop.top);
            if (crop.right > imageRect.right) crop.offset(imageRect.right - crop.right, 0);
            if (crop.bottom > imageRect.bottom) crop.offset(0, imageRect.bottom - crop.bottom);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            resetCrop();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (bitmap == null) return;
            calculateImageRect();
            canvas.drawBitmap(bitmap, null, imageRect, paint);
            drawOverlay(canvas);
        }

        private void drawOverlay(Canvas canvas) {
            canvas.drawRect(
                    imageRect.left,
                    imageRect.top,
                    imageRect.right,
                    crop.top,
                    dim);
            canvas.drawRect(
                    imageRect.left,
                    crop.bottom,
                    imageRect.right,
                    imageRect.bottom,
                    dim);
            canvas.drawRect(
                    imageRect.left,
                    crop.top,
                    crop.left,
                    crop.bottom,
                    dim);
            canvas.drawRect(
                    crop.right,
                    crop.top,
                    imageRect.right,
                    crop.bottom,
                    dim);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4);
            paint.setColor(Color.YELLOW);
            canvas.drawRect(crop, paint);

            paint.setStrokeWidth(2);
            for (int i = 1; i <= 2; i++) {
                float x = crop.left + crop.width() * i / 3f;
                float y = crop.top + crop.height() * i / 3f;
                canvas.drawLine(x, crop.top, x, crop.bottom, paint);
                canvas.drawLine(crop.left, y, crop.right, y, paint);
            }

            paint.setStyle(Paint.Style.FILL);
            float r = 16;
            canvas.drawRect(
                    crop.left - r,
                    crop.top - r,
                    crop.left + r,
                    crop.top + r,
                    paint);
            canvas.drawRect(
                    crop.right - r,
                    crop.top - r,
                    crop.right + r,
                    crop.top + r,
                    paint);
            canvas.drawRect(
                    crop.left - r,
                    crop.bottom - r,
                    crop.left + r,
                    crop.bottom + r,
                    paint);
            canvas.drawRect(
                    crop.right - r,
                    crop.bottom - r,
                    crop.right + r,
                    crop.bottom + r,
                    paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (bitmap == null || crop.isEmpty()) return true;
            float x = e.getX();
            float y = e.getY();
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                mode = hit(x, y);
                lastX = x;
                lastY = y;
                return true;
            }
            if (e.getAction() == MotionEvent.ACTION_MOVE && mode != 0) {
                moveCrop(x - lastX, y - lastY, x, y);
                lastX = x;
                lastY = y;
                invalidate();
                return true;
            }
            if (e.getAction() == MotionEvent.ACTION_UP
                    || e.getAction() == MotionEvent.ACTION_CANCEL) {
                mode = 0;
                return true;
            }
            return true;
        }

        private int hit(float x, float y) {
            float d = 44;
            if (Math.abs(x - crop.left) < d
                    && Math.abs(y - crop.top) < d) {
                return 1;
            }
            if (Math.abs(x - crop.right) < d
                    && Math.abs(y - crop.top) < d) {
                return 2;
            }
            if (Math.abs(x - crop.left) < d
                    && Math.abs(y - crop.bottom) < d) {
                return 3;
            }
            if (Math.abs(x - crop.right) < d
                    && Math.abs(y - crop.bottom) < d) {
                return 4;
            }
            if (crop.contains(x, y)) return 5;
            return 0;
        }

        private void moveCrop(float dx, float dy, float x, float y) {
            float min = 60;
            if (mode == 5) {
                crop.offset(dx, dy);
                if (crop.left < imageRect.left) {
                    crop.offset(imageRect.left - crop.left, 0);
                }
                if (crop.top < imageRect.top) {
                    crop.offset(0, imageRect.top - crop.top);
                }
                if (crop.right > imageRect.right) {
                    crop.offset(imageRect.right - crop.right, 0);
                }
                if (crop.bottom > imageRect.bottom) {
                    crop.offset(0, imageRect.bottom - crop.bottom);
                }
                return;
            }
            if (lockedAspectRatio > 0f) {
                resizeLockedCrop(x, y);
                return;
            }
            if (mode == 1 || mode == 3) {
                crop.left = clamp(
                        x,
                        imageRect.left,
                        crop.right - min);
            }
            if (mode == 2 || mode == 4) {
                crop.right = clamp(
                        x,
                        crop.left + min,
                        imageRect.right);
            }
            if (mode == 1 || mode == 2) {
                crop.top = clamp(
                        y,
                        imageRect.top,
                        crop.bottom - min);
            }
            if (mode == 3 || mode == 4) {
                crop.bottom = clamp(
                        y,
                        crop.top + min,
                        imageRect.bottom);
            }
        }

        private float clamp(float v, float min, float max) {
            return Math.max(min, Math.min(max, v));
        }

        private void fitAspectCrop(RectF available) {
            float width = available.width();
            float height = width / lockedAspectRatio;
            if (height > available.height()) {
                height = available.height();
                width = height * lockedAspectRatio;
            }
            float left = available.left + (available.width() - width) / 2f;
            float top = available.top + (available.height() - height) / 2f;
            crop.set(left, top, left + width, top + height);
        }

        private void resizeLockedCrop(float x, float y) {
            float anchorX = (mode == 1 || mode == 3) ? crop.right : crop.left;
            float anchorY = (mode == 1 || mode == 2) ? crop.bottom : crop.top;
            float signX = x < anchorX ? -1f : 1f;
            float signY = y < anchorY ? -1f : 1f;
            float wantedW = Math.max(60f, Math.abs(x - anchorX));
            float wantedH = Math.max(60f, Math.abs(y - anchorY));
            float width = wantedW;
            float height = width / lockedAspectRatio;
            if (height > wantedH) {
                height = wantedH;
                width = height * lockedAspectRatio;
            }
            float maxW = signX < 0 ? anchorX - imageRect.left : imageRect.right - anchorX;
            float maxH = signY < 0 ? anchorY - imageRect.top : imageRect.bottom - anchorY;
            width = Math.min(width, Math.min(maxW, maxH * lockedAspectRatio));
            height = width / lockedAspectRatio;
            if (width < 60f || height < 60f) return;
            float left = signX < 0 ? anchorX - width : anchorX;
            float right = signX < 0 ? anchorX : anchorX + width;
            float top = signY < 0 ? anchorY - height : anchorY;
            float bottom = signY < 0 ? anchorY : anchorY + height;
            crop.set(left, top, right, bottom);
        }

        Bitmap makeCrop() {
            if (bitmap == null
                    || imageRect.isEmpty()
                    || crop.isEmpty()) {
                return null;
            }
            float sx = bitmap.getWidth() / imageRect.width();
            float sy = bitmap.getHeight() / imageRect.height();
            int x = Math.max(
                    0,
                    Math.round((crop.left - imageRect.left) * sx));
            int y = Math.max(
                    0,
                    Math.round((crop.top - imageRect.top) * sy));
            int w = Math.min(
                    bitmap.getWidth() - x,
                    Math.round(crop.width() * sx));
            int h = Math.min(
                    bitmap.getHeight() - y,
                    Math.round(crop.height() * sy));
            if (w <= 0 || h <= 0) return null;
            return Bitmap.createBitmap(bitmap, x, y, w, h);
        }
    }
}
