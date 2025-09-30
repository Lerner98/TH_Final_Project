package com.example.asltranslator.utils;

import com.example.asltranslator.models.LessonProgress;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Manages Merkle tree operations for verifying lesson progress integrity.
 * Provides methods to compute Merkle roots, generate proofs, and verify progress entries.
 * Data Flow: LessonProgress objects → Hashing → Merkle Tree Construction → Root/Proof Output.
 */

public class MerkleTreeManager {
    private static MerkleTreeManager instance;

    public static synchronized MerkleTreeManager getInstance() {
        if (instance == null) {
            instance = new MerkleTreeManager();
        }
        return instance;
    }

    private MerkleTreeManager() {}

    /**
     * Computes the Merkle root hash from a collection of lesson progress entries.
     * @param progressList Collection of LessonProgress objects to hash.
     * @return The Merkle root hash as a hexadecimal string, or "empty" hash if the list is null/empty.
     */
    public String computeMerkleRoot(Collection<LessonProgress> progressList) {
        if (progressList == null || progressList.isEmpty()) {
            return sha256Hash("empty");
        }

        List<String> hashes = new ArrayList<>();
        // Create leaf nodes by hashing each lesson progress data
        for (LessonProgress progress : progressList) {
            String data = progress.getLessonId() + "|" +
                    progress.getScore() + "|" +
                    progress.getTimestamp() + "|" +
                    progress.isCompleted();
            hashes.add(sha256Hash(data));
        }

        // Build Merkle tree bottom-up by pairing and hashing nodes
        while (hashes.size() > 1) {
            List<String> newLevel = new ArrayList<>();


            for (int i = 0; i < hashes.size(); i += 2) {
                String left = hashes.get(i);
                String right = (i + 1 < hashes.size()) ? hashes.get(i + 1) : left;
                newLevel.add(sha256Hash(left + right));
            }

            hashes = newLevel;
        }

        return hashes.get(0);
    }

    /**
     * Generates a simplified Merkle proof for a specific lesson progress entry.
     * @param allProgress Collection of all lesson progress entries.
     * @param targetLessonId The ID of the lesson to generate proof for.
     * @return A comma-separated string of sibling hashes, or empty if proof cannot be generated.
     */
    public String generateMerkleProof(Collection<LessonProgress> allProgress,
                                      String targetLessonId) {
        // Simplified Merkle proof generation
        // In production, this would return the sibling hashes needed to verify
        List<String> proofPath = new ArrayList<>();

        // Collect hashes of all progress entries except the target
        for (LessonProgress progress : allProgress) {
            if (!progress.getLessonId().equals(targetLessonId)) {
                String hash = sha256Hash(progress.getLessonId() + "|" +
                        progress.getScore() + "|" +
                        progress.getTimestamp());
                proofPath.add(hash);
            }
        }

        // Return concatenated proof path
        return String.join(",", proofPath);
    }

//    /**
//     * Verifies if a lesson progress entry belongs to a given Merkle root.
//     * @param merkleRoot The expected Merkle root hash.
//     * @param progress The LessonProgress object to verify.
//     * @param merkleProof The proof path to reconstruct the root (simplified implementation).
//     * @return True if the progress is valid (basic check), false otherwise.
//     */
//    public boolean verifyProgress(String merkleRoot, LessonProgress progress,
//                                  String merkleProof) {
//        // Verify that a specific progress entry is part of the Merkle tree
//        // This is a simplified implementation
//        String progressHash = sha256Hash(progress.getLessonId() + "|" +
//                progress.getScore() + "|" +
//                progress.getTimestamp() + "|" +
//                progress.isCompleted());
//
//        // In a full implementation, we would reconstruct the root using the proof
//        // For now, we'll do a simple verification
//        return merkleRoot != null && !merkleRoot.isEmpty();
//    }

    private String sha256Hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return "";
        }
    }
}