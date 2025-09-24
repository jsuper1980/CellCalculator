package j2.basic.utils.calc;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.lang.reflect.Method;

/**
 * 表达式计算器
 * 支持数学运算、逻辑运算、内置函数和Java类调用
 */
public class ExpressionEvaluator {

  private final CellCalculator calculator;
  private static final MathContext MATH_CONTEXT = new MathContext(34, RoundingMode.HALF_UP);

  // 运算符优先级
  private static final Map<String, Integer> OPERATOR_PRECEDENCE;
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

  // 函数调用正则表达式
  private static final Pattern FUNCTION_PATTERN = Pattern.compile(
      "(\\w+)\\s*\\(([^()]*)\\)");

  // 单元格引用正则表达式
  private static final Pattern CELL_REFERENCE_PATTERN = Pattern.compile(
      "\\b([\\p{L}_][\\p{L}\\p{N}_]*)\\b");

  public ExpressionEvaluator(CellCalculator calculator) {
    this.calculator = calculator;
  }

  /**
   * 计算表达式
   */
  public Object evaluate(String expression) {
    if (expression == null || expression.trim().isEmpty()) {
      return null;
    }

    expression = expression.trim();

    // 处理字符串字面量
    if (expression.startsWith("'") && expression.endsWith("'") && expression.length() > 1) {
      return expression.substring(1, expression.length() - 1);
    }

    // 替换单元格引用
    expression = replaceCellReferences(expression);

    // 处理函数调用
    expression = processFunctions(expression);

    // 计算表达式
    return evaluateArithmetic(expression);
  }

  /**
   * 替换单元格引用为实际值
   */
  private String replaceCellReferences(String expression) {
    Matcher matcher = CELL_REFERENCE_PATTERN.matcher(expression);
    StringBuffer sb = new StringBuffer();

    while (matcher.find()) {
      String cellId = matcher.group(1);

      // 检查是否是内置函数
      if (isBuiltInFunction(cellId)) {
        continue;
      }

      // 检查是否是有效的单元格ID
      if (isValidCellId(cellId)) {
        try {
          Object value = calculator.getCellValue(cellId);
          String replacement = formatValueForExpression(value);
          matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        } catch (Exception e) {
          throw new RuntimeException("无法获取单元格值: " + cellId + " - " + e.getMessage());
        }
      }
    }
    matcher.appendTail(sb);

    return sb.toString();
  }

  /**
   * 处理函数调用
   */
  private String processFunctions(String expression) {
    while (true) {
      Matcher matcher = FUNCTION_PATTERN.matcher(expression);
      if (!matcher.find()) {
        break;
      }

      String functionName = matcher.group(1);
      String argsString = matcher.group(2);

      Object result = callFunction(functionName, argsString);
      String replacement = formatValueForExpression(result);

      expression = expression.substring(0, matcher.start()) +
          replacement +
          expression.substring(matcher.end());
    }

    return expression;
  }

  /**
   * 调用函数
   */
  private Object callFunction(String functionName, String argsString) {
    List<Object> args = parseArguments(argsString);

    switch (functionName.toLowerCase()) {
      // 基础数学函数
      case "sqrt":
        return sqrt(args);
      case "abs":
        return abs(args);
      case "ceil":
        return ceil(args);
      case "floor":
        return floor(args);
      case "round":
        return round(args);

      // 三角函数
      case "sin":
        return sin(args);
      case "cos":
        return cos(args);
      case "tan":
        return tan(args);
      case "asin":
        return asin(args);
      case "acos":
        return acos(args);
      case "atan":
        return atan(args);

      // 双曲函数
      case "sinh":
        return sinh(args);
      case "cosh":
        return cosh(args);
      case "tanh":
        return tanh(args);

      // 对数函数
      case "log":
        return log(args);
      case "log10":
        return log10(args);
      case "exp":
        return exp(args);

      // 多参数函数
      case "pow":
        return pow(args);
      case "min":
        return min(args);
      case "max":
        return max(args);
      case "avg":
        return avg(args);

      // 逻辑函数
      case "and":
        return and(args);
      case "or":
        return or(args);
      case "not":
        return not(args);
      case "xor":
        return xor(args);
      case "if":
        return ifFunction(args);
      case "ifs":
        return ifs(args);

      // Java类调用
      case "jcall":
        return jcall(args);

      default:
        throw new RuntimeException("未知函数: " + functionName);
    }
  }

  /**
   * 解析函数参数
   */
  private List<Object> parseArguments(String argsString) {
    List<Object> args = new ArrayList<>();

    if (argsString == null || argsString.trim().isEmpty()) {
      return args;
    }

    // 简单的参数分割（不处理嵌套括号）
    String[] parts = argsString.split(",");
    for (String part : parts) {
      part = part.trim();
      if (!part.isEmpty()) {
        Object value = evaluate(part);
        args.add(value);
      }
    }

    return args;
  }

  /**
   * 计算算术表达式
   */
  private Object evaluateArithmetic(String expression) {
    // 使用递归下降解析器
    return parseExpression(new TokenIterator(tokenize(expression)));
  }

  /**
   * 词法分析
   */
  private List<String> tokenize(String expression) {
    List<String> tokens = new ArrayList<>();
    StringBuilder current = new StringBuilder();

    for (int i = 0; i < expression.length(); i++) {
      char c = expression.charAt(i);

      if (Character.isWhitespace(c)) {
        if (current.length() > 0) {
          tokens.add(current.toString());
          current.setLength(0);
        }
      } else if (isOperatorChar(c)) {
        if (current.length() > 0) {
          tokens.add(current.toString());
          current.setLength(0);
        }

        // 处理多字符运算符
        String op = String.valueOf(c);
        if (i + 1 < expression.length()) {
          char next = expression.charAt(i + 1);
          String twoChar = op + next;
          if (OPERATOR_PRECEDENCE.containsKey(twoChar)) {
            tokens.add(twoChar);
            i++;
            continue;
          }
        }

        tokens.add(op);
      } else {
        current.append(c);
      }
    }

    if (current.length() > 0) {
      tokens.add(current.toString());
    }

    return tokens;
  }

  /**
   * 解析表达式
   */
  private Object parseExpression(TokenIterator tokens) {
    return parseLogicalOr(tokens);
  }

  private Object parseLogicalOr(TokenIterator tokens) {
    Object left = parseLogicalAnd(tokens);

    while (tokens.hasNext() && "||".equals(tokens.peek())) {
      tokens.next(); // consume ||
      Object right = parseLogicalAnd(tokens);
      left = logicalOr(left, right);
    }

    return left;
  }

  private Object parseLogicalAnd(TokenIterator tokens) {
    Object left = parseComparison(tokens);

    while (tokens.hasNext() && "&&".equals(tokens.peek())) {
      tokens.next(); // consume &&
      Object right = parseComparison(tokens);
      left = logicalAnd(left, right);
    }

    return left;
  }

  private Object parseComparison(TokenIterator tokens) {
    Object left = parseAddition(tokens);

    while (tokens.hasNext()) {
      String op = tokens.peek();
      if (!"==".equals(op) && !"!=".equals(op) && !"<".equals(op) &&
          !"<=".equals(op) && !">".equals(op) && !">=".equals(op)) {
        break;
      }

      tokens.next(); // consume operator
      Object right = parseAddition(tokens);
      left = compare(left, right, op);
    }

    return left;
  }

  private Object parseAddition(TokenIterator tokens) {
    Object left = parseMultiplication(tokens);

    while (tokens.hasNext()) {
      String op = tokens.peek();
      if (!"+".equals(op) && !"-".equals(op)) {
        break;
      }

      tokens.next(); // consume operator
      Object right = parseMultiplication(tokens);

      if ("+".equals(op)) {
        left = add(left, right);
      } else {
        left = subtract(left, right);
      }
    }

    return left;
  }

  private Object parseMultiplication(TokenIterator tokens) {
    Object left = parsePower(tokens);

    while (tokens.hasNext()) {
      String op = tokens.peek();
      if (!"*".equals(op) && !"/".equals(op) && !"\\".equals(op) && !"%".equals(op)) {
        break;
      }

      tokens.next(); // consume operator
      Object right = parsePower(tokens);

      switch (op) {
        case "*":
          left = multiply(left, right);
          break;
        case "/":
          left = divide(left, right);
          break;
        case "\\":
          left = integerDivide(left, right);
          break;
        case "%":
          left = modulo(left, right);
          break;
      }
    }

    return left;
  }

  private Object parsePower(TokenIterator tokens) {
    Object left = parseUnary(tokens);

    if (tokens.hasNext() && "^".equals(tokens.peek())) {
      tokens.next(); // consume ^
      Object right = parsePower(tokens); // 右结合
      left = power(left, right);
    }

    return left;
  }

  private Object parseUnary(TokenIterator tokens) {
    if (tokens.hasNext()) {
      String token = tokens.peek();
      if ("-".equals(token)) {
        tokens.next();
        Object operand = parseUnary(tokens);
        return negate(operand);
      } else if ("+".equals(token)) {
        tokens.next();
        return parseUnary(tokens);
      }
    }

    return parsePrimary(tokens);
  }

  private Object parsePrimary(TokenIterator tokens) {
    if (!tokens.hasNext()) {
      throw new RuntimeException("表达式不完整");
    }

    String token = tokens.next();

    if ("(".equals(token)) {
      Object result = parseExpression(tokens);
      if (!tokens.hasNext() || !")".equals(tokens.next())) {
        throw new RuntimeException("缺少右括号");
      }
      return result;
    }

    // 解析数值或布尔值
    return parseValue(token);
  }

  /**
   * 解析值
   */
  private Object parseValue(String value) {
    if ("true".equalsIgnoreCase(value)) {
      return Boolean.TRUE;
    }
    if ("false".equalsIgnoreCase(value)) {
      return Boolean.FALSE;
    }

    try {
      return new BigDecimal(value, MATH_CONTEXT);
    } catch (NumberFormatException e) {
      return value; // 字符串
    }
  }

  // ==================== 运算方法 ====================

  private Object add(Object a, Object b) {
    if (a instanceof BigDecimal && b instanceof BigDecimal) {
      return ((BigDecimal) a).add((BigDecimal) b, MATH_CONTEXT);
    }
    throw new RuntimeException("加法运算需要数值类型");
  }

  private Object subtract(Object a, Object b) {
    if (a instanceof BigDecimal && b instanceof BigDecimal) {
      return ((BigDecimal) a).subtract((BigDecimal) b, MATH_CONTEXT);
    }
    throw new RuntimeException("减法运算需要数值类型");
  }

  private Object multiply(Object a, Object b) {
    if (a instanceof BigDecimal && b instanceof BigDecimal) {
      return ((BigDecimal) a).multiply((BigDecimal) b, MATH_CONTEXT);
    }
    throw new RuntimeException("乘法运算需要数值类型");
  }

  private Object divide(Object a, Object b) {
    if (a instanceof BigDecimal && b instanceof BigDecimal) {
      BigDecimal divisor = (BigDecimal) b;
      if (divisor.compareTo(BigDecimal.ZERO) == 0) {
        throw new RuntimeException("除零错误");
      }
      return ((BigDecimal) a).divide(divisor, MATH_CONTEXT);
    }
    throw new RuntimeException("除法运算需要数值类型");
  }

  private Object integerDivide(Object a, Object b) {
    if (a instanceof BigDecimal && b instanceof BigDecimal) {
      BigDecimal dividend = (BigDecimal) a;
      BigDecimal divisor = (BigDecimal) b;
      if (divisor.compareTo(BigDecimal.ZERO) == 0) {
        throw new RuntimeException("除零错误");
      }
      return dividend.divideToIntegralValue(divisor);
    }
    throw new RuntimeException("整数除法运算需要数值类型");
  }

  private Object modulo(Object a, Object b) {
    if (a instanceof BigDecimal && b instanceof BigDecimal) {
      BigDecimal dividend = (BigDecimal) a;
      BigDecimal divisor = (BigDecimal) b;
      if (divisor.compareTo(BigDecimal.ZERO) == 0) {
        throw new RuntimeException("除零错误");
      }
      BigDecimal result = dividend.remainder(divisor, MATH_CONTEXT);
      // 确保结果非负
      if (result.compareTo(BigDecimal.ZERO) < 0) {
        result = result.add(divisor.abs());
      }
      return result;
    }
    throw new RuntimeException("模运算需要数值类型");
  }

  private Object power(Object a, Object b) {
    if (a instanceof BigDecimal && b instanceof BigDecimal) {
      BigDecimal base = (BigDecimal) a;
      BigDecimal exponent = (BigDecimal) b;

      // 使用Math.pow进行计算，然后转换为BigDecimal
      double result = Math.pow(base.doubleValue(), exponent.doubleValue());
      return new BigDecimal(result, MATH_CONTEXT);
    }
    throw new RuntimeException("幂运算需要数值类型");
  }

  private Object negate(Object a) {
    if (a instanceof BigDecimal) {
      return ((BigDecimal) a).negate();
    }
    throw new RuntimeException("取负运算需要数值类型");
  }

  private Object compare(Object a, Object b, String op) {
    int cmp;

    if (a instanceof BigDecimal && b instanceof BigDecimal) {
      cmp = ((BigDecimal) a).compareTo((BigDecimal) b);
    } else if (a instanceof Boolean && b instanceof Boolean) {
      cmp = ((Boolean) a).compareTo((Boolean) b);
    } else if (a instanceof String && b instanceof String) {
      cmp = ((String) a).compareTo((String) b);
    } else {
      // 类型不匹配时的比较
      String aStr = a != null ? a.toString() : "null";
      String bStr = b != null ? b.toString() : "null";
      cmp = aStr.compareTo(bStr);
    }

    switch (op) {
      case "==":
        return cmp == 0;
      case "!=":
        return cmp != 0;
      case "<":
        return cmp < 0;
      case "<=":
        return cmp <= 0;
      case ">":
        return cmp > 0;
      case ">=":
        return cmp >= 0;
      default:
        throw new RuntimeException("未知比较运算符: " + op);
    }
  }

  private Object logicalAnd(Object a, Object b) {
    return isTrue(a) && isTrue(b);
  }

  private Object logicalOr(Object a, Object b) {
    return isTrue(a) || isTrue(b);
  }

  private boolean isTrue(Object value) {
    if (value instanceof Boolean) {
      return (Boolean) value;
    }
    if (value instanceof BigDecimal) {
      return ((BigDecimal) value).compareTo(BigDecimal.ZERO) != 0;
    }
    if (value instanceof String) {
      return !((String) value).isEmpty();
    }
    return value != null;
  }

  // ==================== 内置函数实现 ====================

  private Object sqrt(List<Object> args) {
    if (args.size() != 1) {
      throw new RuntimeException("sqrt函数需要1个参数");
    }
    BigDecimal value = toBigDecimal(args.get(0));
    if (value.compareTo(BigDecimal.ZERO) < 0) {
      throw new RuntimeException("sqrt函数参数不能为负数");
    }
    return new BigDecimal(Math.sqrt(value.doubleValue()), MATH_CONTEXT);
  }

  private Object abs(List<Object> args) {
    if (args.size() != 1) {
      throw new RuntimeException("abs函数需要1个参数");
    }
    BigDecimal value = toBigDecimal(args.get(0));
    return value.abs();
  }

  private Object ceil(List<Object> args) {
    if (args.size() != 1) {
      throw new RuntimeException("ceil函数需要1个参数");
    }
    BigDecimal value = toBigDecimal(args.get(0));
    return new BigDecimal(Math.ceil(value.doubleValue()));
  }

  private Object floor(List<Object> args) {
    if (args.size() != 1) {
      throw new RuntimeException("floor函数需要1个参数");
    }
    BigDecimal value = toBigDecimal(args.get(0));
    return new BigDecimal(Math.floor(value.doubleValue()));
  }

  private Object round(List<Object> args) {
    if (args.size() == 1) {
      BigDecimal value = toBigDecimal(args.get(0));
      return value.setScale(0, RoundingMode.HALF_UP);
    } else if (args.size() == 2) {
      BigDecimal value = toBigDecimal(args.get(0));
      int digits = toBigDecimal(args.get(1)).intValue();
      return value.setScale(digits, RoundingMode.HALF_UP);
    } else {
      throw new RuntimeException("round函数需要1或2个参数");
    }
  }

  private Object sin(List<Object> args) {
    if (args.size() != 1) {
      throw new RuntimeException("sin函数需要1个参数");
    }
    BigDecimal value = toBigDecimal(args.get(0));
    return new BigDecimal(Math.sin(value.doubleValue()), MATH_CONTEXT);
  }

  private Object cos(List<Object> args) {
    if (args.size() != 1) {
      throw new RuntimeException("cos函数需要1个参数");
    }
    BigDecimal value = toBigDecimal(args.get(0));
    return new BigDecimal(Math.cos(value.doubleValue()), MATH_CONTEXT);
  }

  private Object tan(List<Object> args) {
    if (args.size() != 1) {
      throw new RuntimeException("tan函数需要1个参数");
    }
    BigDecimal value = toBigDecimal(args.get(0));
    return new BigDecimal(Math.tan(value.doubleValue()), MATH_CONTEXT);
  }

  private Object asin(List<Object> args) {
    if (args.size() != 1) {
      throw new RuntimeException("asin函数需要1个参数");
    }
    BigDecimal value = toBigDecimal(args.get(0));
    return new BigDecimal(Math.asin(value.doubleValue()), MATH_CONTEXT);
  }

  private Object acos(List<Object> args) {
    if (args.size() != 1) {
      throw new RuntimeException("acos函数需要1个参数");
    }
    BigDecimal value = toBigDecimal(args.get(0));
    return new BigDecimal(Math.acos(value.doubleValue()), MATH_CONTEXT);
  }

  private Object atan(List<Object> args) {
    if (args.size() != 1) {
      throw new RuntimeException("atan函数需要1个参数");
    }
    BigDecimal value = toBigDecimal(args.get(0));
    return new BigDecimal(Math.atan(value.doubleValue()), MATH_CONTEXT);
  }

  private Object sinh(List<Object> args) {
    if (args.size() != 1) {
      throw new RuntimeException("sinh函数需要1个参数");
    }
    BigDecimal value = toBigDecimal(args.get(0));
    return new BigDecimal(Math.sinh(value.doubleValue()), MATH_CONTEXT);
  }

  private Object cosh(List<Object> args) {
    if (args.size() != 1) {
      throw new RuntimeException("cosh函数需要1个参数");
    }
    BigDecimal value = toBigDecimal(args.get(0));
    return new BigDecimal(Math.cosh(value.doubleValue()), MATH_CONTEXT);
  }

  private Object tanh(List<Object> args) {
    if (args.size() != 1) {
      throw new RuntimeException("tanh函数需要1个参数");
    }
    BigDecimal value = toBigDecimal(args.get(0));
    return new BigDecimal(Math.tanh(value.doubleValue()), MATH_CONTEXT);
  }

  private Object log(List<Object> args) {
    if (args.size() != 1) {
      throw new RuntimeException("log函数需要1个参数");
    }
    BigDecimal value = toBigDecimal(args.get(0));
    if (value.compareTo(BigDecimal.ZERO) <= 0) {
      throw new RuntimeException("log函数参数必须大于0");
    }
    return new BigDecimal(Math.log(value.doubleValue()), MATH_CONTEXT);
  }

  private Object log10(List<Object> args) {
    if (args.size() != 1) {
      throw new RuntimeException("log10函数需要1个参数");
    }
    BigDecimal value = toBigDecimal(args.get(0));
    if (value.compareTo(BigDecimal.ZERO) <= 0) {
      throw new RuntimeException("log10函数参数必须大于0");
    }
    return new BigDecimal(Math.log10(value.doubleValue()), MATH_CONTEXT);
  }

  private Object exp(List<Object> args) {
    if (args.size() != 1) {
      throw new RuntimeException("exp函数需要1个参数");
    }
    BigDecimal value = toBigDecimal(args.get(0));
    return new BigDecimal(Math.exp(value.doubleValue()), MATH_CONTEXT);
  }

  private Object pow(List<Object> args) {
    if (args.size() != 2) {
      throw new RuntimeException("pow函数需要2个参数");
    }
    BigDecimal base = toBigDecimal(args.get(0));
    BigDecimal exponent = toBigDecimal(args.get(1));
    return new BigDecimal(Math.pow(base.doubleValue(), exponent.doubleValue()), MATH_CONTEXT);
  }

  private Object min(List<Object> args) {
    if (args.isEmpty()) {
      throw new RuntimeException("min函数至少需要1个参数");
    }
    BigDecimal result = toBigDecimal(args.get(0));
    for (int i = 1; i < args.size(); i++) {
      BigDecimal value = toBigDecimal(args.get(i));
      if (value.compareTo(result) < 0) {
        result = value;
      }
    }
    return result;
  }

  private Object max(List<Object> args) {
    if (args.isEmpty()) {
      throw new RuntimeException("max函数至少需要1个参数");
    }
    BigDecimal result = toBigDecimal(args.get(0));
    for (int i = 1; i < args.size(); i++) {
      BigDecimal value = toBigDecimal(args.get(i));
      if (value.compareTo(result) > 0) {
        result = value;
      }
    }
    return result;
  }

  private Object avg(List<Object> args) {
    if (args.isEmpty()) {
      throw new RuntimeException("avg函数至少需要1个参数");
    }
    BigDecimal sum = BigDecimal.ZERO;
    for (Object arg : args) {
      sum = sum.add(toBigDecimal(arg));
    }
    return sum.divide(new BigDecimal(args.size()), MATH_CONTEXT);
  }

  private Object and(List<Object> args) {
    for (Object arg : args) {
      if (!isTrue(arg)) {
        return Boolean.FALSE;
      }
    }
    return Boolean.TRUE;
  }

  private Object or(List<Object> args) {
    for (Object arg : args) {
      if (isTrue(arg)) {
        return Boolean.TRUE;
      }
    }
    return Boolean.FALSE;
  }

  private Object not(List<Object> args) {
    if (args.size() != 1) {
      throw new RuntimeException("not函数需要1个参数");
    }
    return isTrue(args.get(0)) ? Boolean.FALSE : Boolean.TRUE;
  }

  private Object xor(List<Object> args) {
    int trueCount = 0;
    for (Object arg : args) {
      if (isTrue(arg)) {
        trueCount++;
      }
    }
    return (trueCount % 2 == 1) ? Boolean.TRUE : Boolean.FALSE;
  }

  private Object ifFunction(List<Object> args) {
    if (args.size() != 3) {
      throw new RuntimeException("if函数需要3个参数");
    }
    return isTrue(args.get(0)) ? args.get(1) : args.get(2);
  }

  private Object ifs(List<Object> args) {
    if (args.size() < 2) {
      throw new RuntimeException("ifs函数至少需要2个参数");
    }

    // 如果是奇数个参数，最后一个是默认值
    int pairCount = args.size() / 2;
    boolean hasDefault = args.size() % 2 == 1;

    // 检查条件-值对
    for (int i = 0; i < pairCount; i++) {
      if (isTrue(args.get(i * 2))) {
        return args.get(i * 2 + 1);
      }
    }

    // 如果有默认值，返回默认值
    if (hasDefault) {
      return args.get(args.size() - 1);
    }

    throw new RuntimeException("ifs函数没有匹配的条件且没有默认值");
  }

  private Object jcall(List<Object> args) {
    if (args.size() < 2) {
      throw new RuntimeException("jcall函数至少需要2个参数（类名和方法名）");
    }

    String className = args.get(0).toString();
    String methodName = args.get(1).toString();
    Object[] methodArgs = args.subList(2, args.size()).toArray();

    try {
      Class<?> clazz = Class.forName(className);

      // 查找匹配的方法
      Method[] methods = clazz.getMethods();
      for (Method method : methods) {
        if (method.getName().equals(methodName) &&
            method.getParameterCount() == methodArgs.length &&
            java.lang.reflect.Modifier.isStatic(method.getModifiers())) {

          // 转换参数类型
          Class<?>[] paramTypes = method.getParameterTypes();
          Object[] convertedArgs = new Object[methodArgs.length];

          for (int i = 0; i < methodArgs.length; i++) {
            convertedArgs[i] = convertArgument(methodArgs[i], paramTypes[i]);
          }

          Object result = method.invoke(null, convertedArgs);
          return convertResult(result);
        }
      }

      throw new RuntimeException("找不到匹配的静态方法: " + className + "." + methodName);

    } catch (Exception e) {
      throw new RuntimeException("Java方法调用失败: " + e.getMessage());
    }
  }

  // ==================== 辅助方法 ====================

  private BigDecimal toBigDecimal(Object value) {
    if (value instanceof BigDecimal) {
      return (BigDecimal) value;
    }
    if (value instanceof Number) {
      return new BigDecimal(value.toString(), MATH_CONTEXT);
    }
    if (value instanceof String) {
      try {
        return new BigDecimal((String) value, MATH_CONTEXT);
      } catch (NumberFormatException e) {
        throw new RuntimeException("无法转换为数值: " + value);
      }
    }
    if (value instanceof Boolean) {
      return (Boolean) value ? BigDecimal.ONE : BigDecimal.ZERO;
    }
    throw new RuntimeException("无法转换为数值: " + value);
  }

  private Object convertArgument(Object arg, Class<?> targetType) {
    if (targetType.isAssignableFrom(arg.getClass())) {
      return arg;
    }

    if (targetType == int.class || targetType == Integer.class) {
      return toBigDecimal(arg).intValue();
    }
    if (targetType == long.class || targetType == Long.class) {
      return toBigDecimal(arg).longValue();
    }
    if (targetType == float.class || targetType == Float.class) {
      return toBigDecimal(arg).floatValue();
    }
    if (targetType == double.class || targetType == Double.class) {
      return toBigDecimal(arg).doubleValue();
    }
    if (targetType == boolean.class || targetType == Boolean.class) {
      return isTrue(arg);
    }
    if (targetType == String.class) {
      return arg.toString();
    }

    return arg;
  }

  private Object convertResult(Object result) {
    if (result instanceof Number && !(result instanceof BigDecimal)) {
      return new BigDecimal(result.toString(), MATH_CONTEXT);
    }
    return result;
  }

  private String formatValueForExpression(Object value) {
    if (value == null) {
      return "null";
    }
    if (value instanceof Boolean) {
      return value.toString(); // 布尔值不加引号
    }
    if (value instanceof String) {
      // 检查字符串是否已经被引号包围
      String str = (String) value;
      if ((str.startsWith("\"") && str.endsWith("\"")) ||
          (str.startsWith("'") && str.endsWith("'"))) {
        return str; // 已经有引号，直接返回
      }
      return "\"" + str + "\""; // 字符串用双引号
    }
    return value.toString();
  }

  private boolean isOperatorChar(char c) {
    return "+-*/\\%^()=!<>&|".indexOf(c) >= 0;
  }

  private boolean isBuiltInFunction(String name) {
    return Set.of("sqrt", "abs", "ceil", "floor", "round", "sin", "cos", "tan",
        "asin", "acos", "atan", "sinh", "cosh", "tanh", "log", "log10",
        "exp", "pow", "min", "max", "avg", "and", "or", "not", "xor",
        "if", "ifs", "jcall").contains(name.toLowerCase());
  }

  private boolean isValidCellId(String id) {
    // 将 true 和 false 作为保留字，不能作为 CellID
    if ("true".equalsIgnoreCase(id) || "false".equalsIgnoreCase(id)) {
      return false;
    }
    return Pattern.matches("^[\\p{L}_][\\p{L}\\p{N}_]*$", id);
  }
}
