package com.example.urlshortener.shorten;

/**
 * Encodes a non-negative long ID into a base62 string using the alphabet mapping from design.md §5:
 * 0-9 -> '0'-'9', 10-35 -> 'a'-'z', 36-61 -> 'A'-'Z'.
 */
public final class Base62Encoder {

  private static final String ALPHABET =
      "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
  private static final int BASE = ALPHABET.length();

  private Base62Encoder() {}

  public static String encode(long id) {
    if (id < 0) {
      throw new IllegalArgumentException("id must be non-negative: " + id);
    }
    if (id == 0) {
      return String.valueOf(ALPHABET.charAt(0));
    }
    StringBuilder sb = new StringBuilder();
    long value = id;
    while (value > 0) {
      sb.append(ALPHABET.charAt((int) (value % BASE)));
      value /= BASE;
    }
    return sb.reverse().toString();
  }
}
