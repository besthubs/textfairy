/*
 * Copyright (C) 2007 The Android Open Source Project
 * Copyright (C) 2012,2013 Renard Wellnitz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.renard.ocr.cropimage;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;

import com.googlecode.leptonica.android.Bilinear;
import com.googlecode.leptonica.android.Box;
import com.googlecode.leptonica.android.Clip;
import com.googlecode.leptonica.android.Pix;
import com.googlecode.leptonica.android.Rotate;
import com.googlecode.leptonica.android.Scale;
import com.googlecode.leptonica.android.WriteFile;
import com.renard.ocr.DocumentGridActivity;
import com.renard.ocr.R;
import com.renard.ocr.help.HintDialog;
import com.renard.util.Util;

/**
 * The activity can crop specific region of interest from an image.
 */
public class CropImageActivity extends MonitoredActivity {
    private static final int HINT_DIALOG_ID = 2;

    private final Handler mHandler = new Handler();

    private int mRotation = 0;

    boolean mSaving; // Whether the "save" button is already clicked.
    private Pix mPix; // original Picture
    private Pix mPixScaled; // scaled Picture
    private CropImageView mImageView;

    private Bitmap mBitmap;
    private HighlightView mCrop;
    private float mScaleFactor = 1;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        getWindow().setFormat(PixelFormat.RGBA_8888);
        // requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_cropimage);

        mImageView = (CropImageView) findViewById(R.id.image);
        mImageView.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {

            @Override
            public void onGlobalLayout() {
                //TODO not run on UI Thread
                Intent intent = getIntent();
                Bundle extras = intent.getExtras();
                if (extras != null) {
                    // mImagePath = extras.getString("image-path");
                    // mSaveUri = Uri.fromFile(new File(mImagePath));
                    // mBitmap = getBitmap(mImagePath);
                    mPix = new Pix(extras.getLong(DocumentGridActivity.EXTRA_NATIVE_PIX));

                    // scale it so that it fits the screen
                    float bestScale = 1 / getScaleFactorToFitScreen(mPix, mImageView.getWidth(), mImageView.getHeight());
                    mScaleFactor = Util.determineScaleFactor(mPix.getWidth(), mPix.getHeight(), mImageView.getWidth(), mImageView.getHeight());

                    if (mScaleFactor == 0) {
                        mScaleFactor = 1;
                    } else {
                        if (bestScale < 1 && bestScale > 0.5f) {
                            mScaleFactor = (float) (1 / Math.pow(2, 0.5f));
                        } else if (bestScale <= 0.5f) {
                            mScaleFactor = (float) (1 / Math.pow(2, 0.25f));
                        } else {
                            mScaleFactor = 1 / mScaleFactor;
                        }
                    }

                    mPixScaled = Scale.scaleWithoutFiltering(mPix, mScaleFactor);

                    mBitmap = WriteFile.writeBitmap(mPixScaled);
                    mRotation = extras.getInt(DocumentGridActivity.EXTRA_ROTATION) / 90;
                }

                if (mBitmap == null) {
                    finish();
                    return;
                }

                mImageView.setImageBitmapResetBase(mBitmap, true, mRotation * 90);
                makeDefault();
                mImageView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
            }

        });

        initAppIcon(this, HINT_DIALOG_ID);
    }

    private float getScaleFactorToFitScreen(Pix mPix, int vwidth, int vheight) {
        float scale;
        int dWidth = mPix.getWidth();
        int dHeight = mPix.getHeight();
        if (dWidth <= vwidth && dHeight <= vheight) {
            scale = 1.0f;
        } else {
            scale = Math.min((float) vwidth / (float) dWidth, (float) vheight / (float) dHeight);
        }
        return scale;
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle args) {
        switch (id) {
            case HINT_DIALOG_ID:
                return HintDialog.createDialog(this, R.string.crop_help_title, "file:///android_res/raw/crop_help.html");
        }
        return super.onCreateDialog(id, args);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.item_save) {
            onSaveClicked();
            return true;
        } else if (itemId == R.id.item_rotate_right) {
            onRotateClicked(1);
            return true;
        } else if (itemId == R.id.item_rotate_left) {
            onRotateClicked(-1);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.crop_image_options, menu);
        return true;
    }

    private void onRotateClicked(int delta) {
        if (delta < 0) {
            delta = -delta * 3;
        }
        mRotation += delta;
        mRotation = mRotation % 4;
        mImageView.setImageBitmapResetBase(mBitmap, false, mRotation * 90);
        makeDefault();
    }

    private Box adjustBoundsToMultipleOf4(float f, float g, float h, float j) {
        int newLeft = (int) f;
        int newTop = (int) g;
        int newRight = (int) (f + h);
        int newBottom = (int) (g + j);

        int wDiff = ((int) h) % 4;
        int hDiff = ((int) j) % 4;
        for (int i = 0; i < wDiff; i++) {
            if (i % 2 == 0) {
                newLeft++;
            } else {
                newRight--;
            }
        }
        for (int i = 0; i < hDiff; i++) {
            if (i % 2 == 0) {
                newTop++;
            } else {
                newBottom--;
            }
        }
        return new Box(newLeft, newTop, newRight - newLeft, newBottom - newTop);

    }

    private void onSaveClicked() {
        if (mSaving)
            return;

        if (mCrop == null) {
            return;
        }

        mSaving = true;

        Util.startBackgroundJob(this, null, getText(R.string.cropping_image).toString(), new Runnable() {
            public void run() {
                try {
                    Rect r = mCrop.getCropRect();
                    final float[] trapezoid = mCrop.getTrapezoid();

                    Box bb = getBoundingBox(r, mScaleFactor);
                    Pix croppedPix = Clip.clipRectangle(mPix, bb);
                    if (croppedPix == null) {
                        throw new IllegalStateException();
                    }
                    float scale = 1f / mScaleFactor;
                    Matrix scaleMatrix = new Matrix();
                    scaleMatrix.setScale(scale, scale);
                    scaleMatrix.postTranslate(-bb.getX(), -bb.getY());
                    scaleMatrix.mapPoints(trapezoid);


                    final float[] dest = new float[]{0, 0, bb.getWidth(), 0, bb.getWidth(), bb.getHeight(), 0, bb.getHeight()};
                    Pix bilinear = Bilinear.bilinear(croppedPix, dest, trapezoid);
                    if (bilinear == null) {
                        bilinear = croppedPix;
                    } else {
                        croppedPix.recycle();
                    }


                    if (mRotation != 0 && mRotation != 4) {
                        Pix rotatedPix = Rotate.rotateOrth(bilinear, mRotation);
                        bilinear.recycle();
                        bilinear = rotatedPix;
                    }
                    if (bilinear == null) {
                        throw new IllegalStateException();
                    }
                    Intent result = new Intent();
                    result.putExtra(DocumentGridActivity.EXTRA_NATIVE_PIX, bilinear.getNativePix());
                    setResult(RESULT_OK, result);
                } catch (IllegalStateException e) {
                    setResult(RESULT_CANCELED);
                } finally {
                    mPix.recycle();
                    finish();
                }
            }
        }, mHandler);

    }

    private Box getBoundingBox(Rect r, float scaleFactor) {
        float scale = 1f / scaleFactor;
        final int left = (int) (r.left * scale);
        final int top = (int) (r.top * scale);
        final int width = (int) ((r.right - r.left) * scale);
        final int height = (int) ((r.bottom - r.top) * scale);
        return new Box(left, top, width, height);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        setResult(RESULT_CANCELED);
        mPix.recycle();
    }

    @Override
    protected void onDestroy() {
        mPixScaled.recycle();
        super.onDestroy();
    }

    // Create a default HightlightView if we found no face in the picture.
    private void makeDefault() {

        int width = mBitmap.getWidth();
        int height = mBitmap.getHeight();

        Rect imageRect = new Rect(0, 0, width, height);

        // make the default size about 4/5 of the width or height
        int cropWidth = Math.min(width, height) * 4 / 5;
        int cropHeight = cropWidth;


        int x = (width - cropWidth) / 2;
        int y = (height - cropHeight) / 2;

        RectF cropRect = new RectF(x, y, x + cropWidth, y + cropHeight);

        HighlightView hv = new HighlightView(mImageView, imageRect, cropRect);

        mImageView.add(hv);
        mImageView.invalidate();
        mCrop = hv;
        mCrop.setFocus(true);
    }

}

