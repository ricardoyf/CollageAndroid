package com.ricardo.reencuadrador.v5;

import android.app.Activity;
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
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int REQ_TREE = 17;
    private static final String PREFS = "reencuadre_estado";
    private static final String SUFFIX = "_ENC";

    private final ArrayList<Photo> photos = new ArrayList<>();
    private final Set<String> processed = new HashSet<>();

    private TextView info;
    private CropView cropView;
    private Uri treeUri;
    private Uri rootDocUri;
    private String treeKey;
    private int index = 0;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        Uri saved = getSavedTree();
        if (saved != null) {
            loadFolder(saved);
        } else {
            info.setText("Pulsa Carpeta y elige DCIM, Camera o la carpeta donde estén las fotos.");
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(18, 18, 18));

        info = new TextView(this);
        info.setTextColor(Color.WHITE);
        info.setTextSize(15);
        info.setPadding(14, 10, 14, 8);
        root.addView(info, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        cropView = new CropView(this);
        root.addView(cropView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(8, 8, 8, 12);
        bar.setOrientation(LinearLayout.HORIZONTAL);

        Button folder = button("Carpeta");
        Button reset = button("Reset");
        Button skip = button("Saltar");
        Button accept = button("Aceptar");

        folder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickFolder();
            }
        });
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cropView.resetCrop();
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

        bar.addView(folder);
        bar.addView(reset);
        bar.addView(skip);
        bar.addView(accept);
        root.addView(bar, new LinearLayout.LayoutParams(
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
        processed.clear();
        processed.addAll(getSharedPreferences(PREFS, MODE_PRIVATE)
                .getStringSet(treeKey, new HashSet<String>()));

        photos.clear();
        index = 0;

        try {
            scanFolder(DocumentsContract.getTreeDocumentId(treeUri), "");
        } catch (Exception e) {
            info.setText("No pude leer esa carpeta. Elige una carpeta normal de almacenamiento interno o SD.");
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        saveProcessed();
        showCurrent();
    }

    private void scanFolder(String folderDocId, String relativePath) {
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                folderDocId);
        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };

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
                if (name == null || docId == null) continue;

                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    scanFolder(docId, relativePath + name + "/");
                    continue;
                }

                if (!isSupportedPhoto(name, mime)) continue;
                String key = relativePath + name;
                if (processed.contains(key)) continue;

                String outName = outputName(name);
                if (documentExists(childrenUri, outName)) {
                    processed.add(key);
                    continue;
                }

                Uri docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId);
                Uri parentDocUri = DocumentsContract.buildDocumentUriUsingTree(
                        treeUri,
                        folderDocId);
                photos.add(new Photo(
                        key,
                        name,
                        mime,
                        docUri,
                        childrenUri,
                        parentDocUri));
            }
        }
    }

    private boolean isSupportedPhoto(String name, String mime) {
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains(SUFFIX.toLowerCase(Locale.ROOT) + ".")) return false;
        if (mime != null && mime.startsWith("image/")) return true;
        return lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".png")
                || lower.endsWith(".webp")
                || lower.endsWith(".bmp");
    }

    private boolean documentExists(Uri childrenUri, String displayName) {
        String[] projection = {
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
        };
        try (Cursor cursor = getContentResolver().query(
                childrenUri,
                projection,
                null,
                null,
                null)) {
            if (cursor == null) return false;
            while (cursor.moveToNext()) {
                if (displayName.equals(cursor.getString(0))) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
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
            info.setText((index + 1) + "/" + photos.size() + " - " + p.name
                    + " | Pendientes: " + (photos.size() - index));
        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "No pude abrir " + p.name,
                    Toast.LENGTH_SHORT).show();
            index++;
            showCurrent();
        }
    }

    private Bitmap loadBitmapRespectingOrientation(Uri uri) throws Exception {
        Bitmap bitmap;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            bitmap = BitmapFactory.decodeStream(in);
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

    private void skip() {
        if (photos.isEmpty()) return;
        index++;
        showCurrent();
    }

    private void accept() {
        if (photos.isEmpty()
                || index >= photos.size()
                || cropView.bitmap == null) {
            return;
        }
        Photo p = photos.get(index);
        Bitmap cropped = cropView.makeCrop();
        if (cropped == null) return;

        String outName = outputName(p.name);
        String mime = outputMime(p.name, p.mime);
        try {
            deleteIfExists(outName);
            Uri out = DocumentsContract.createDocument(
                    getContentResolver(),
                    p.parentDocUri,
                    mime,
                    outName);
            if (out == null) {
                throw new IllegalStateException("Android no creó el archivo de salida");
            }
            try (OutputStream os = getContentResolver().openOutputStream(out)) {
                Bitmap.CompressFormat format = mime.equals("image/png")
                        ? Bitmap.CompressFormat.PNG
                        : mime.equals("image/webp")
                        ? Bitmap.CompressFormat.WEBP
                        : Bitmap.CompressFormat.JPEG;
                cropped.compress(format, 95, os);
            }
            processed.add(p.key);
            saveProcessed();
            index++;
            showCurrent();
        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "Error al guardar: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void deleteIfExists(String displayName) {
        if (photos.isEmpty() || index >= photos.size()) return;
        Uri childrenUri = photos.get(index).childrenUri;
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

    private String outputName(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0) return name + SUFFIX + ".jpg";
        return name.substring(0, dot) + SUFFIX + name.substring(dot);
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
        final String mime;
        final Uri uri;
        final Uri childrenUri;
        final Uri parentDocUri;

        Photo(
                String key,
                String name,
                String mime,
                Uri uri,
                Uri childrenUri,
                Uri parentDocUri) {
            this.key = key;
            this.name = name;
            this.mime = mime;
            this.uri = uri;
            this.childrenUri = childrenUri;
            this.parentDocUri = parentDocUri;
        }
    }

    public static class CropView extends View {
        Bitmap bitmap;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF imageRect = new RectF();
        private final RectF crop = new RectF();
        private int mode = 0;
        private float lastX;
        private float lastY;

        public CropView(android.content.Context context) {
            super(context);
            dim.setColor(Color.argb(135, 0, 0, 0));
            setBackgroundColor(Color.rgb(18, 18, 18));
        }

        void setBitmap(Bitmap b) {
            bitmap = b;
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
                        crop.set(
                                imageRect.left + mx,
                                imageRect.top + my,
                                imageRect.right - mx,
                                imageRect.bottom - my);
                    } else {
                        crop.setEmpty();
                    }
                    invalidate();
                }
            });
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

