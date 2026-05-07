package com.vibedev.util;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SensitiveWordFilter {

    private static class Node {
        Map<Character, Node> children = new HashMap<>();
        Node fail;
        String word; // non-null means this is a terminal node
        int depth;
    }

    private volatile Node root = new Node();
    private final Object buildLock = new Object();

    public void rebuild(List<String> words) {
        synchronized (buildLock) {
            Node newRoot = new Node();
            // Build trie
            for (String w : words) {
                if (w == null || w.isEmpty()) continue;
                Node cur = newRoot;
                for (int i = 0; i < w.length(); i++) {
                    char c = w.charAt(i);
                    final int depth = i + 1;
                    cur = cur.children.computeIfAbsent(c, k -> {
                        Node n = new Node();
                        n.depth = depth;
                        return n;
                    });
                }
                cur.word = w;
            }
            // Build failure links (BFS)
            Queue<Node> queue = new LinkedList<>();
            for (Node child : newRoot.children.values()) {
                child.fail = newRoot;
                queue.offer(child);
            }
            while (!queue.isEmpty()) {
                Node cur = queue.poll();
                for (Map.Entry<Character, Node> entry : cur.children.entrySet()) {
                    char c = entry.getKey();
                    Node child = entry.getValue();
                    Node fail = cur.fail;
                    while (fail != null && !fail.children.containsKey(c)) {
                        fail = fail.fail;
                    }
                    child.fail = fail == null ? newRoot : fail.children.getOrDefault(c, newRoot);
                    queue.offer(child);
                }
            }
            this.root = newRoot;
        }
    }

    public List<MatchResult> findMatches(String text) {
        if (text == null || text.isEmpty()) return List.of();
        List<MatchResult> results = new ArrayList<>();
        Node cur = root;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            while (cur != root && !cur.children.containsKey(c)) {
                cur = cur.fail;
            }
            cur = cur.children.getOrDefault(c, root);
            // Collect matches at this position
            Node temp = cur;
            while (temp != root && temp.word != null) {
                int start = i - temp.depth + 1;
                results.add(new MatchResult(temp.word, start));
                temp = temp.fail;
            }
        }
        return results;
    }

    public boolean hasSensitive(String text) {
        return !findMatches(text).isEmpty();
    }

    public record MatchResult(String word, int position) {}
}
