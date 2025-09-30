package com.example.asltranslator.network;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Compresses and decompresses Bitmap images for optimized transmission.
 * Uses JPEG compression and Deflater/Inflater for data size reduction.
 * Currently used for internal compression logic; planned for frame transmission in CameraTranslateActivity and PracticeActivity.
 */
public class FrameCompressor {
    private static final String TAG = "FrameCompressor";
    private static final int COMPRESSION_QUALITY = 100;

    /**
     * Compresses a Bitmap to a JPEG byte array with Deflater compression.
     * Intended for reducing frame size before server transmission (currently unused).
     *
     * @param bitmap The Bitmap to compress.
     * @return Compressed byte array or null on failure.
     */
    public byte[] compress(Bitmap bitmap) {
        try {
            // Resize bitmap to 640x480
            Bitmap resized = Bitmap.createScaledBitmap(
                    bitmap,
                    640,
                    480,
                    true
            );

            // Convert to JPEG
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            resized.compress(Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, baos);
            byte[] jpegData = baos.toByteArray();

            // Apply Deflater compression
            byte[] compressedData = deflate(jpegData);

            Log.d(TAG, "Compression: " + jpegData.length + " -> " + compressedData.length + " bytes");

            return compressedData;

        } catch (Exception e) {
            Log.e(TAG, "Compression failed", e);
            return null;
        }
    }

    /**
     * Applies Deflater compression to a byte array.
     *
     * @param data Data to compress.
     * @return Compressed byte array or original data on failure.
     */
    private byte[] deflate(byte[] data) {
        try {
            Deflater deflater = new Deflater(Deflater.BEST_SPEED);
            deflater.setInput(data);
            deflater.finish();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
            byte[] buffer = new byte[1024];

            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                outputStream.write(buffer, 0, count);
            }

            outputStream.close();
            return outputStream.toByteArray();

        } catch (Exception e) {
            Log.e(TAG, "Deflate failed", e);
            return data;
        }
    }

    /**
     * Decompresses a byte array to a Bitmap using Inflater and BitmapFactory.
     * Intended for processing compressed server responses (currently unused).
     *
     * @param compressedData Compressed byte array.
     * @return Decompressed Bitmap or null on failure.
     */
    public Bitmap decompress(byte[] compressedData) {
        try {
            // Inflate the data
            byte[] inflatedData = inflate(compressedData);

            // Decode JPEG
            return BitmapFactory.decodeByteArray(inflatedData, 0, inflatedData.length);

        } catch (Exception e) {
            Log.e(TAG, "Decompression failed", e);
            return null;
        }
    }

    /**
     * Inflates a compressed byte array using Inflater.
     *
     * @param data Compressed data.
     * @return Inflated byte array or original data on failure.
     */
    private byte[] inflate(byte[] data) {
        try {
            Inflater inflater = new Inflater();
            inflater.setInput(data);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
            byte[] buffer = new byte[1024];

            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                outputStream.write(buffer, 0, count);
            }

            outputStream.close();
            return outputStream.toByteArray();

        } catch (Exception e) {
            Log.e(TAG, "Inflate failed", e);
            return data;
        }
    }
}