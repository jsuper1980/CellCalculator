package j2.basic.utils.calc;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 计算器公共工具类
 * 
 * 提供 CellCalculator 和 ExpressionEvaluator 共用的工具方法，
 * 减少代码重复，提高代码复用性。
 */
public class CalculatorUtils {

  /**
   * 数学计算上下文 - 34位精度，四舍五入
   */
  public static final MathContext MATH_CONTEXT = new MathContext(34, RoundingMode.HALF_UP);

  /**
   * 内置函数集合
   */
  public static final Set<String> BUILT_IN_FUNCTIONS = Set.of(
      "sqrt", "abs", "ceil", "floor", "round", "sin", "cos", "tan",
      "asin", "acos", "atan", "sinh", "cosh", "tanh", "log", "log10",
      "exp", "pow", "min", "max", "avg", "and", "or", "not", "xor",
      "if", "ifs", "jcall");

  /**
   * 单元格ID验证模式
   */
  public static final Pattern CELL_ID_PATTERN = Pattern.compile(
      "^[\\p{L}_][\\p{L}\\p{N}_]*$");

  /**
   * 单元格引用模式
   */
  public static final Pattern CELL_REFERENCE_PATTERN = Pattern.compile(
      "\\b([\\p{L}_][\\p{L}\\p{N}_]*)\\b");

  /**
   * 函数调用模式
   */
  public static final Pattern FUNCTION_PATTERN = Pattern.compile(
      "(\\w+)\\s*\\(([^()]*)\\)");

  /**
   * Java方法调用模式
   */
  public static final Pattern JCALL_PATTERN = Pattern.compile(
      "jcall\\s*\\(([^()]*)\\)");

  /**
   * 运算符优先级
   */
  public static final Map<String, Integer> OPERATOR_PRECEDENCE;
  static {
    Map<String, Integer> precedence = new HashMap<>();
    precedence.put("||", 1);
    precedence.put("&&", 2);
    precedence.put("==", 3);
    precedence.put("!=", 3);
    precedence.put("<", 3);
    precedence.put("<=", 3);
    precedence.put(">", 3);
    precedence.put(">=", 3);
    precedence.put("+", 4);
    precedence.put("-", 4);
    precedence.put("*", 5);
    precedence.put("/", 5);
    precedence.put("\\", 5);
    precedence.put("%", 5);
    precedence.put("^", 6);
    OPERATOR_PRECEDENCE = Collections.unmodifiableMap(precedence);
  }

  /**
   * 解析字符串值为适当的对象类型
   * 
   * 解析优先级：
   * 1. 布尔值（true/false，忽略大小写）
   * 2. 数值（BigDecimal）
   * 3. 字符串
   * 
   * @param value 待解析的字符串
   * @return 解析后的对象
   */
  public static Object parseValue(String value) {
    if (value == null) {
      return null;
    }

    value = value.trim();

    // 字符串字面量解析（带引号的字符串）
    if ((value.startsWith("'") && value.endsWith("'")) ||
        (value.startsWith("\"") && value.endsWith("\""))) {
      return value.substring(1, value.length() - 1);
    }

    // 布尔值解析
    if ("true".equalsIgnoreCase(value)) {
      return Boolean.TRUE;
    }
    if ("false".equalsIgnoreCase(value)) {
      return Boolean.FALSE;
    }

    // 数值解析
    try {
      return new BigDecimal(value, MATH_CONTEXT);
    } catch (NumberFormatException e) {
      // 解析失败，作为字符串处理
      return value;
    }
  }

  /**
   * 将对象转换为 BigDecimal
   * 
   * @param value 要转换的对象
   * @return BigDecimal 值
   * @throws IllegalArgumentException 如果无法转换为数值
   */
  public static BigDecimal toBigDecimal(Object value) {
    if (value == null) {
      throw new IllegalArgumentException("Cannot convert null to BigDecimal");
    }
    if (value instanceof BigDecimal) {
      return (BigDecimal) value;
    }
    if (value instanceof Number) {
      return new BigDecimal(value.toString(), MATH_CONTEXT);
    }
    if (value instanceof Boolean) {
      return ((Boolean) value) ? BigDecimal.ONE : BigDecimal.ZERO;
    }
    if (value instanceof String) {
      try {
        return new BigDecimal((String) value, MATH_CONTEXT);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Cannot convert '" + value + "' to BigDecimal", e);
      }
    }
    throw new IllegalArgumentException("Cannot convert " + value.getClass().getSimpleName() + " to BigDecimal");
  }

  /**
   * 格式化数值为字符串
   * 
   * 优化显示：
   * 1. 移除尾随零
   * 2. 对于大数或小数使用科学计数法
   * 
   * @param number 要格式化的数值
   * @return 格式化后的字符串
   */
  public static String formatNumber(BigDecimal number) {
    if (number == null) {
      return "null";
    }

    // 移除尾随零
    BigDecimal stripped = number.stripTrailingZeros();

    // 对于非常大或非常小的数，使用科学计数法
    if (stripped.scale() < -6 || stripped.scale() > 10) {
      return stripped.toString();
    }

    // 使用普通表示法
    return stripped.toPlainString();
  }

  /**
   * 格式化值用于表达式
   * 
   * @param value 要格式化的值
   * @return 格式化后的字符串
   */
  public static String formatValueForExpression(Object value) {
    if (value == null) {
      return "null";
    }
    if (value instanceof Boolean) {
      return value.toString(); // 布尔值不加引号
    }
    if (value instanceof String) {
      String str = (String) value;
      // 对于字符串，需要添加引号以确保在后续解析中被正确识别为字符串字面量
      return "'" + str.replace("'", "\\'") + "'";
    }
    return value.toString();
  }

  /**
   * 验证单元格ID是否有效
   * 
   * 有效的单元格ID规则：
   * - 以字母或下划线开头
   * - 后续字符可以是字母、数字或下划线
   * - 不能是保留字（true、false）
   * 
   * @param id 单元格ID
   * @return 是否为有效的单元格ID
   */
  public static boolean isValidCellId(String id) {
    if (id == null || id.trim().isEmpty()) {
      return false;
    }

    // 将 true 和 false 作为保留字，不能作为 CellID
    if ("true".equalsIgnoreCase(id) || "false".equalsIgnoreCase(id)) {
      return false;
    }

    return CELL_ID_PATTERN.matcher(id).matches();
  }

  /**
   * 判断名称是否为内置函数
   * 
   * @param name 函数名称
   * @return 是否为内置函数
   */
  public static boolean isBuiltInFunction(String name) {
    return name != null && BUILT_IN_FUNCTIONS.contains(name.toLowerCase());
  }

  /**
   * 判断字符是否为运算符字符
   * 
   * @param c 要判断的字符
   * @return 是否为运算符字符
   */
  public static boolean isOperatorChar(char c) {
    return "+-*/\\%^()=!<>&|".indexOf(c) >= 0;
  }

  /**
   * 格式化单元格值为显示字符串
   * 
   * @param value 单元格值
   * @return 格式化后的字符串
   */
  public static String formatCellValue(Object value) {
    if (value == null) {
      return "";
    }
    if (value instanceof BigDecimal) {
      return formatNumber((BigDecimal) value);
    }
    if (value instanceof Boolean) {
      return ((Boolean) value) ? "TRUE" : "FALSE";
    }
    return value.toString();
  }

  // 私有构造函数，防止实例化
  private CalculatorUtils() {
    throw new UnsupportedOperationException("Utility class cannot be instantiated");
  }
}
