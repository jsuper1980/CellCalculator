package j2.basic.utils.calc;

/**
 * 数学工具类 - 用于演示Java类调用功能
 * 提供各种数学计算方法供ExcelEngine调用
 */
public class MathUtils {

    /**
     * 计算阶乘
     * 
     * @param n 输入数字
     * @return n的阶乘
     */
    public static double factorial(double n) {
        System.out.println("调用自定义的阶乘函数，参数为: " + n);
        if (n < 0) {
            throw new IllegalArgumentException("阶乘函数的参数不能为负数: " + n);
        }
        if (n == 0 || n == 1) {
            return 1;
        }
        double result = 1;
        for (int i = 2; i <= (int) n; i++) {
            result *= i;
        }
        return result;
    }

    /**
     * 计算斐波那契数列第n项
     * 
     * @param n 项数
     * @return 第n项的值
     */
    public static double fibonacci(double n) {
        int num = (int) n;
        if (num < 0) {
            throw new IllegalArgumentException("斐波那契数列的参数不能为负数: " + n);
        }
        if (num <= 1) {
            return num;
        }

        double a = 0, b = 1;
        for (int i = 2; i <= num; i++) {
            double temp = a + b;
            a = b;
            b = temp;
        }
        return b;
    }

    /**
     * 计算幂运算
     * 
     * @param base 底数
     * @param exponent 指数
     * @return base的exponent次幂
     */
    public static double power(double base, double exponent) {
        return Math.pow(base, exponent);
    }

    /**
     * 计算最大公约数
     * 
     * @param a 第一个数
     * @param b 第二个数
     * @return 最大公约数
     */
    public static double gcd(double a, double b) {
        int x = Math.abs((int) a);
        int y = Math.abs((int) b);

        while (y != 0) {
            int temp = y;
            y = x % y;
            x = temp;
        }
        return x;
    }

    /**
     * 计算最小公倍数
     * 
     * @param a 第一个数
     * @param b 第二个数
     * @return 最小公倍数
     */
    public static double lcm(double a, double b) {
        return Math.abs(a * b) / gcd(a, b);
    }

    /**
     * 判断是否为质数
     * 
     * @param n 输入数字
     * @return 1表示是质数，0表示不是质数
     */
    public static double isPrime(double n) {
        int num = (int) n;
        if (num < 2) {
            return 0;
        }
        if (num == 2) {
            return 1;
        }
        if (num % 2 == 0) {
            return 0;
        }

        for (int i = 3; i * i <= num; i += 2) {
            if (num % i == 0) {
                return 0;
            }
        }
        return 1;
    }

    /**
     * 计算组合数 C(n, r)
     * 
     * @param n 总数
     * @param r 选择数
     * @return 组合数
     */
    public static double combination(double n, double r) {
        if (r > n || r < 0 || n < 0) {
            return 0;
        }
        if (r == 0 || r == n) {
            return 1;
        }

        // 优化计算，选择较小的r
        if (r > n - r) {
            r = n - r;
        }

        double result = 1;
        for (int i = 0; i < r; i++) {
            result = result * (n - i) / (i + 1);
        }
        return result;
    }

    /**
     * 计算排列数 P(n, r)
     * 
     * @param n 总数
     * @param r 选择数
     * @return 排列数
     */
    public static double permutation(double n, double r) {
        if (r > n || r < 0 || n < 0) {
            return 0;
        }

        double result = 1;
        for (int i = 0; i < r; i++) {
            result *= (n - i);
        }
        return result;
    }

    /**
     * 计算平方根（牛顿法实现）
     * 
     * @param x 输入数字
     * @return 平方根
     */
    public static double customSqrt(double x) {
        if (x < 0) {
            throw new IllegalArgumentException("平方根函数的参数不能为负数: " + x);
        }
        if (x == 0) {
            return 0;
        }

        double guess = x / 2;
        double epsilon = 1e-10;

        while (Math.abs(guess * guess - x) > epsilon) {
            guess = (guess + x / guess) / 2;
        }
        return guess;
    }

    /**
     * 计算数字的各位数字之和
     * 
     * @param n 输入数字
     * @return 各位数字之和
     */
    public static double digitSum(double n) {
        int num = Math.abs((int) n);
        int sum = 0;

        while (num > 0) {
            sum += num % 10;
            num /= 10;
        }
        return sum;
    }

    /**
     * 计算数字的位数
     * 
     * @param n 输入数字
     * @return 位数
     */
    public static double digitCount(double n) {
        if (n == 0) {
            return 1;
        }
        return Math.floor(Math.log10(Math.abs(n))) + 1;
    }
}
