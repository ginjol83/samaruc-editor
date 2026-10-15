package com.retroeditor.service.atari.basic;

public final class AtariBcd {

    private static final double[] EXP_TAB = {
        1e-136, 1e-134, 1e-132, 1e-130, 1e-128, 1e-126, 1e-124, 1e-122, 1e-120,
        1e-118, 1e-116, 1e-114, 1e-112, 1e-110, 1e-108, 1e-106, 1e-104, 1e-102, 1e-100,
        1e-98, 1e-96, 1e-94, 1e-92, 1e-90, 1e-88, 1e-86, 1e-84, 1e-82, 1e-80,
        1e-78, 1e-76, 1e-74, 1e-72, 1e-70, 1e-68, 1e-66, 1e-64, 1e-62, 1e-60,
        1e-58, 1e-56, 1e-54, 1e-52, 1e-50, 1e-48, 1e-46, 1e-44, 1e-42, 1e-40,
        1e-38, 1e-36, 1e-34, 1e-32, 1e-30, 1e-28, 1e-26, 1e-24, 1e-22, 1e-20,
        1e-18, 1e-16, 1e-14, 1e-12, 1e-10, 1e-8, 1e-6, 1e-4, 1e-2, 1e0,
        1e2, 1e4, 1e6, 1e8, 1e10, 1e12, 1e14, 1e16, 1e18, 1e20,
        1e22, 1e24, 1e26, 1e28, 1e30, 1e32, 1e34, 1e36, 1e38, 1e40,
        1e42, 1e44, 1e46, 1e48, 1e50, 1e52, 1e54, 1e56, 1e58, 1e60,
        1e62, 1e64, 1e66, 1e68, 1e70, 1e72, 1e74, 1e76, 1e78, 1e80,
        1e82, 1e84, 1e86, 1e88, 1e90, 1e92, 1e94, 1e96, 1e98, 1e100,
        1e102, 1e104, 1e106, 1e108, 1e110, 1e112, 1e114, 1e116, 1e118
    };

    private final int exp;
    private final int[] digits;

    private AtariBcd(int exp, int[] digits) {
        this.exp = exp;
        this.digits = digits;
    }

    public int exp() {
        return exp;
    }

    public int digit(int index) {
        return digits[index];
    }

    static AtariBcd of(int exp, int d0, int d1, int d2, int d3, int d4) {
        return new AtariBcd(exp, new int[] { d0, d1, d2, d3, d4 });
    }

    public static AtariBcd fromDouble(double x) {
        int exp = 0;
        int[] dig = new int[5];
        if (x == 0.0) {
            return new AtariBcd(0, dig);
        }
        if (x < 0) {
            exp = 0x80;
            x = -x;
        }
        if (x < 1e-99) {
            return new AtariBcd(exp, dig);
        }
        if (x >= 1e98) {
            exp |= 0x71;
            for (int i = 0; i < 5; i++) {
                dig[i] = 0x99;
            }
            return new AtariBcd(exp, dig);
        }
        exp |= 0x0E;
        for (int i = 19; i < 118; i++, exp++) {
            if (x < EXP_TAB[i]) {
                long n = (long) (0.5 + x * 10000000000.0 / EXP_TAB[i]);
                for (int d = 4; d > 0; d--) {
                    dig[d] = toBcd((int) (n % 100));
                    n /= 100;
                }
                dig[0] = toBcd((int) n);
                break;
            }
        }
        return new AtariBcd(exp, dig);
    }

    public double toDouble() {
        if (exp == 0) {
            return 0.0;
        }
        if (exp == 0x80) {
            return -0.0;
        }
        double x = (digits[0] >> 4) * 10 + (digits[0] & 0x0F);
        x = x * 100 + (digits[1] >> 4) * 10 + (digits[1] & 0x0F);
        x = x * 100 + (digits[2] >> 4) * 10 + (digits[2] & 0x0F);
        x = x * 100 + (digits[3] >> 4) * 10 + (digits[3] & 0x0F);
        x = x * 100 + (digits[4] >> 4) * 10 + (digits[4] & 0x0F);
        x = x * EXP_TAB[exp & 0x7F];
        return (exp & 0x80) != 0 ? -x : x;
    }

    public String toBasicString() {
        if ((exp & 0x7F) == 0) {
            return "0";
        }
        char[] dig = new char[10];
        for (int i = 0; i < 5; i++) {
            dig[2 * i] = (char) ('0' + (digits[i] >> 4));
            dig[2 * i + 1] = (char) ('0' + (digits[i] & 0x0F));
        }
        int exponent = (exp & 0x7F) * 2 - 136;
        boolean sign = (exp & 0x80) != 0;
        int last = 0;
        for (int i = 9; i > 0; i--) {
            if (dig[i] != '0') {
                last = i;
                break;
            }
            exponent++;
        }
        String d = new String(dig, 0, last + 1);
        if (d.charAt(0) == '0') {
            d = d.substring(1);
        }
        int len = d.length();
        StringBuilder out = new StringBuilder();
        if (sign) {
            out.append('-');
        }
        if (exponent < 0 && exponent >= -len) {
            int pos = 0;
            while (exponent > -len) {
                out.append(d.charAt(pos));
                pos++;
                exponent--;
            }
            out.append('.');
            out.append(d, pos, len);
        } else if (exponent + 2 == 1 - len) {
            out.append('.');
            out.append('0');
            out.append(d);
        } else if (exponent == 0) {
            out.append(d);
        } else if (exponent == 1) {
            out.append(d);
            out.append('0');
        } else if (exponent == 2) {
            out.append(d);
            out.append('0');
            out.append('0');
        } else {
            out.append(d);
            out.append('E');
            if (exponent < 0) {
                out.append('-');
                exponent = -exponent;
            }
            if (exponent > 9) {
                out.append((char) ('0' + (exponent / 10)));
            }
            out.append((char) ('0' + (exponent % 10)));
        }
        return out.toString();
    }

    private static int toBcd(int n) {
        return (n / 10) * 16 + (n % 10);
    }
}
