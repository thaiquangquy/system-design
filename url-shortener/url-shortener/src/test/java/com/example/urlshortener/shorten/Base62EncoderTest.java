package com.example.urlshortener.shorten;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class Base62EncoderTest {

  @Test
  void encodesZeroAsFirstAlphabetCharacter() {
    assertThat(Base62Encoder.encode(0)).isEqualTo("0");
  }

  @Test
  void encodesSingleDigitValues() {
    assertThat(Base62Encoder.encode(9)).isEqualTo("9");
    assertThat(Base62Encoder.encode(10)).isEqualTo("a");
    assertThat(Base62Encoder.encode(35)).isEqualTo("z");
    assertThat(Base62Encoder.encode(36)).isEqualTo("A");
    assertThat(Base62Encoder.encode(61)).isEqualTo("Z");
  }

  @Test
  void encodesMultiDigitValues() {
    assertThat(Base62Encoder.encode(62)).isEqualTo("10");
    assertThat(Base62Encoder.encode(123)).isEqualTo("1Z");
  }

  @Test
  void isDeterministicAndOneToOne() {
    String a = Base62Encoder.encode(2009215674938L);
    String b = Base62Encoder.encode(2009215674938L);
    String other = Base62Encoder.encode(2009215674939L);

    assertThat(a).isEqualTo(b);
    assertThat(a).isNotEqualTo(other);
  }

  @Test
  void rejectsNegativeIds() {
    assertThatThrownBy(() -> Base62Encoder.encode(-1)).isInstanceOf(IllegalArgumentException.class);
  }
}
