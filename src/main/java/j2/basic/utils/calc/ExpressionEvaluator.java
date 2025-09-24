package j2.basic.utils.calc;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/**
 * 表达式计算器
 * 支持数学运算、逻辑运算、内置函数和Java类调用
 */
public class ExpressionEvaluator {

  private final CellCalculator calculator;

  // 运算符优先级和正则表达式已移至 CalculatorUtils

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

    // 检查是否是 jcall 函数调用，如果是则直接处理
    Matcher jcallMatcher = CalculatorUtils.JCALL_PATTERN.matcher(expression);
    if (jcallMatcher.matches()) {
      String argsString = jcallMatcher.group(1);
      return callFunction("jcall", argsString);
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
    // 替换单元格引用为其值
    Matcher matcher = CalculatorUtils.CELL_REFERENCE_PATTERN.matcher(expression);
    StringBuffer sb = new StringBuffer();

    while (matcher.find()) {
      String cellId = matcher.group(1);

      // 检查是否是内置函数
      if (CalculatorUtils.isBuiltInFunction(cellId)) {
        continue;
      }

      // 检查是否是有效的单元格ID
      if (CalculatorUtils.isValidCellId(cellId)) {
        try {
          // 直接获取单元格的已计算值，避免触发重新计算
          Object value = calculator.getRawValue(cellId);
          String replacement = CalculatorUtils.formatValueForExpression(value);
          matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        } catch (Exception e) {
          throw new RuntimeException("无法获取单元格值 " + cellId + " - " + e.getMessage());
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
      Matcher matcher = CalculatorUtils.FUNCTION_PATTERN.matcher(expression);
      if (!matcher.find()) {
        break;
      }

      String functionName = matcher.group(1);
      String argsString = matcher.group(2);

      // 对于 jcall 函数，直接调用而不替换
      if ("jcall".equals(functionName.toLowerCase())) {
        Object result = callFunction(functionName, argsString);
        String replacement = CalculatorUtils.formatValueForExpression(result);
        expression = expression.substring(0, matcher.start()) +
            replacement +
            expression.substring(matcher.end());
      } else {
        Object result = callFunction(functionName, argsString);
        String replacement = CalculatorUtils.formatValueForExpression(result);
        expression = expression.substring(0, matcher.start()) +
            replacement +
            expression.substring(matcher.end());
      }
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
        throw new RuntimeException("未知函数 " + functionName);
    }
  }

  /**
   * 解析函数参数列表
   * 
   * 将逗号分隔的参数字符串解析为参数列表，每个参数都会递归计算
   * 
   * @param argsString 参数字符串，如 "1,2,3" 或 "A1,B2+C3"
   * @return 解析后的参数列表
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
        // 对于字符串参数，直接使用字面值
        if (part.startsWith("'") && part.endsWith("'")) {
          args.add(part.substring(1, part.length() - 1));
        } else if (part.startsWith("\"") && part.endsWith("\"")) {
          args.add(part.substring(1, part.length() - 1));
        } else {
          // 递归计算每个参数的值
          Object value = evaluate(part);
          args.add(value);
        }
      }
    }

    return args;
  }

  /**
   * 计算算术表达式
   * 
   * 使用递归下降解析器解析表达式，确保运算符优先级正确
   * 
   * @param expression 算术表达式
   * @return 计算结果
   */
  private Object evaluateArithmetic(String expression) {
    // 使用递归下降解析器
    return parseExpression(new TokenIterator(tokenize(expression)));
  }

  /**
   * 词法分析 - 将表达式分解为标记（token）列表
   * 
   * 将输入的表达式字符串分解为操作数、运算符、括号等标记，
   * 为后续的语法分析做准备
   * 
   * @param expression 输入表达式
   * @return 标记列表
   */
  private List<String> tokenize(String expression) {
    List<String> tokens = new ArrayList<>();
    StringBuilder current = new StringBuilder();

    for (int i = 0; i < expression.length(); i++) {
      char c = expression.charAt(i);

      if (Character.isWhitespace(c)) {
        // 遇到空白字符，结束当前标记
        if (current.length() > 0) {
          tokens.add(current.toString());
          current.setLength(0);
        }
      } else if (CalculatorUtils.isOperatorChar(c)) {
        // 遇到运算符字符
        if (current.length() > 0) {
          tokens.add(current.toString());
          current.setLength(0);
        }

        // 处理多字符运算符（如 ==、!=、<=、>=、&&、||）
        String op = String.valueOf(c);
        if (i + 1 < expression.length()) {
          char next = expression.charAt(i + 1);
          String twoChar = op + next;
          if (CalculatorUtils.OPERATOR_PRECEDENCE.containsKey(twoChar)) {
            tokens.add(twoChar);
            i++; // 跳过下一个字符
            continue;
          }
        }

        tokens.add(op);
      } else {
        // 普通字符，添加到当前标记
        current.append(c);
      }
    }

    // 添加最后一个标记
    if (current.length() > 0) {
      tokens.add(current.toString());
    }

    return tokens;
  }

  /**
   * 解析表达式的入口方法
   * 
   * @param tokens 标记迭代器
   * @return 表达式计算结果
   */
  private Object parseExpression(TokenIterator tokens) {
    return parseLogicalOr(tokens);
  }

  /**
   * 解析逻辑或运算（||）
   * 
   * 逻辑或运算的优先级最低，因此在解析树的最顶层
   * 
   * @param tokens 标记迭代器
   * @return 逻辑或运算结果
   */
  private Object parseLogicalOr(TokenIterator tokens) {
    Object left = parseLogicalAnd(tokens);

    while (tokens.hasNext() && "||".equals(tokens.peek())) {
      tokens.next(); // consume ||
      Object right = parseLogicalAnd(tokens);
      left = logicalOr(left, right);
    }

    return left;
  }

  /**
   * 解析逻辑与运算（&&）
   * 
   * @param tokens 标记迭代器
   * @return 逻辑与运算结果
   */
  private Object parseLogicalAnd(TokenIterator tokens) {
    Object left = parseComparison(tokens);

    while (tokens.hasNext() && "&&".equals(tokens.peek())) {
      tokens.next(); // consume &&
      Object right = parseComparison(tokens);
      left = logicalAnd(left, right);
    }

    return left;
  }

  /**
   * 解析比较运算（==、!=、<、<=、>、>=）
   * 
   * @param tokens 标记迭代器
   * @return 比较运算结果
   */
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

  /**
   * 解析加减运算（+、-）
   * 
   * @param tokens 标记迭代器
   * @return 加减运算结果
   */
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

  /**
   * 解析乘除模运算（*、/、\、%）
   * 
   * @param tokens 标记迭代器
   * @return 乘除模运算结果
   */
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

  /**
   * 解析幂运算（^）
   * 
   * 幂运算是右结合的，即 2^3^4 = 2^(3^4)
   * 
   * @param tokens 标记迭代器
   * @return 幂运算结果
   */
  private Object parsePower(TokenIterator tokens) {
    Object left = parseUnary(tokens);

    if (tokens.hasNext() && "^".equals(tokens.peek())) {
      tokens.next(); // consume ^
      Object right = parsePower(tokens); // 右结合
      left = power(left, right);
    }

    return left;
  }

  /**
   * 解析一元运算（+、-）
   * 
   * 处理正负号，如 -5、+3
   * 
   * @param tokens 标记迭代器
   * @return 一元运算结果
   */
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

  /**
   * 解析基本元素（数字、布尔值、括号表达式）
   * 
   * @param tokens 标记迭代器
   * @return 基本元素的值
   */
  private Object parsePrimary(TokenIterator tokens) {
    if (!tokens.hasNext()) {
      throw new RuntimeException("表达式不完整");
    }

    String token = tokens.next();

    if ("(".equals(token)) {
      // 括号表达式
      Object result = parseExpression(tokens);
      if (!tokens.hasNext() || !")".equals(tokens.next())) {
        throw new RuntimeException("缺少右括号");
      }
      return result;
    }

    // 解析数值或布尔值
    return CalculatorUtils.parseValue(token);
  }



  // ==================== 运算方法 ====================

  /**
   * 加法运算
   * 
   * @param a 左操作数
   * @param b 右操作数
   * @return 加法结果
   */
  private Object add(Object a, Object b) {
    if (a instanceof BigDecimal && b instanceof BigDecimal) {
      return ((BigDecimal) a).add((BigDecimal) b, CalculatorUtils.MATH_CONTEXT);
    }
    throw new RuntimeException("加法运算需要数值类型");
  }

  /**
   * 减法运算
   * 
   * @param a 左操作数
   * @param b 右操作数
   * @return 减法结果
   */
  private Object subtract(Object a, Object b) {
    if (a instanceof BigDecimal && b instanceof BigDecimal) {
      return ((BigDecimal) a).subtract((BigDecimal) b, CalculatorUtils.MATH_CONTEXT);
    }
    throw new RuntimeException("减法运算需要数值类型");
  }

  /**
   * 乘法运算
   * 
   * @param a 左操作数
   * @param b 右操作数
   * @return 乘法结果
   */
  private Object multiply(Object a, Object b) {
    if (a instanceof BigDecimal && b instanceof BigDecimal) {
      return ((BigDecimal) a).multiply((BigDecimal) b, CalculatorUtils.MATH_CONTEXT);
    }
    throw new RuntimeException("乘法运算需要数值类型");
  }

  /**
   * 除法运算
   * 
   * @param a 被除数
   * @param b 除数
   * @return 除法结果
   * @throws RuntimeException 当除数为零时抛出
   */
  private Object divide(Object a, Object b) {
    if (a instanceof BigDecimal && b instanceof BigDecimal) {
      BigDecimal divisor = (BigDecimal) b;
      if (divisor.compareTo(BigDecimal.ZERO) == 0) {
        throw new RuntimeException("除零错误");
      }
      return ((BigDecimal) a).divide(divisor, CalculatorUtils.MATH_CONTEXT);
    }
    throw new RuntimeException("除法运算需要数值类型");
  }

  /**
   * 整数除法运算
   * 
   * 返回除法的整数部分，不进行四舍五入
   * 
   * @param a 被除数
   * @param b 除数
   * @return 整数除法结果
   */
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

  /**
   * 模运算（取余）
   * 
   * 计算 a % b，确保结果为非负数
   * 
   * @param a 被除数
   * @param b 除数
   * @return 模运算结果
   */
  private Object modulo(Object a, Object b) {
    if (a instanceof BigDecimal && b instanceof BigDecimal) {
      BigDecimal dividend = (BigDecimal) a;
      BigDecimal divisor = (BigDecimal) b;
      if (divisor.compareTo(BigDecimal.ZERO) == 0) {
        throw new RuntimeException("除零错误");
      }
      BigDecimal result = dividend.remainder(divisor, CalculatorUtils.MATH_CONTEXT);
      // 确保结果非负
      if (result.compareTo(BigDecimal.ZERO) < 0) {
        result = result.add(divisor.abs());
      }
      return result;
    }
    throw new RuntimeException("模运算需要数值类型");
  }

  /**
   * 幂运算
   * 
   * 计算 a^b，使用Math.pow进行计算
   * 
   * @param a 底数
   * @param b 指数
   * @return 幂运算结果
   */
  private Object power(Object a, Object b) {
    if (a instanceof BigDecimal && b instanceof BigDecimal) {
      BigDecimal base = (BigDecimal) a;
      BigDecimal exponent = (BigDecimal) b;

      // 使用Math.pow进行计算，然后转换为BigDecimal
      double result = Math.pow(base.doubleValue(), exponent.doubleValue());
      return new BigDecimal(result, CalculatorUtils.MATH_CONTEXT);
    }
    throw new RuntimeException("幂运算需要数值类型");
  }

  /**
   * 取负运算
   * 
   * @param a 操作数
   * @return 取负结果
   */
  private Object negate(Object a) {
    if (a instanceof BigDecimal) {
      return ((BigDecimal) a).negate();
    }
    throw new RuntimeException("取负运算需要数值类型");
  }

  /**
   * 比较运算
   * 
   * 支持数值、布尔值、字符串的比较
   * 
   * @param a 左操作数
   * @param b 右操作数
   * @param op 比较运算符
   * @return 比较结果（布尔值）
   */
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
        throw new RuntimeException("未知比较运算符 " + op);
    }
  }

  /**
   * 逻辑与运算
   * 
   * @param a 左操作数
   * @param b 右操作数
   * @return 逻辑与结果
   */
  private Object logicalAnd(Object a, Object b) {
    return isTrue(a) && isTrue(b);
  }

  /**
   * 逻辑或运算
   * 
   * @param a 左操作数
   * @param b 右操作数
   * @return 逻辑或结果
   */
  private Object logicalOr(Object a, Object b) {
    return isTrue(a) || isTrue(b);
  }

  /**
   * 判断值是否为真
   * 
   * 判断规则：
   * - Boolean类型：直接返回布尔值
   * - BigDecimal类型：非零为真
   * - String类型：非空为真
   * - 其他类型：非null为真
   * 
   * @param value 要判断的值
   * @return 是否为真
   */
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

  /**
   * 平方根函数
   * 
   * @param args 参数列表，应包含1个数值参数
   * @return 平方根结果
   */
  private Object sqrt(List<Object> args) {
    if (args.size() != 1) {
      throw new IllegalArgumentException("sqrt函数需要1个参数");
    }

    BigDecimal value = CalculatorUtils.toBigDecimal(args.get(0));
    if (value.compareTo(BigDecimal.ZERO) < 0) {
      throw new ArithmeticException("不能计算负数的平方根");
    }

    // 使用牛顿法计算平方根
    BigDecimal x = value;
    BigDecimal lastX;
    do {
      lastX = x;
      x = x.add(value.divide(x, CalculatorUtils.MATH_CONTEXT)).divide(new BigDecimal("2"), CalculatorUtils.MATH_CONTEXT);
    } while (x.subtract(lastX).abs().compareTo(new BigDecimal("1E-" + CalculatorUtils.MATH_CONTEXT.getPrecision())) > 0);

    return x;
  }

  private Object abs(List<Object> args) {
    if (args.size() != 1) {
      throw new IllegalArgumentException("abs函数需要1个参数");
    }

    BigDecimal value = CalculatorUtils.toBigDecimal(args.get(0));
    return value.abs();
  }

  private Object ceil(List<Object> args) {
    if (args.size() != 1) {
      throw new IllegalArgumentException("ceil函数需要1个参数");
    }

    BigDecimal value = CalculatorUtils.toBigDecimal(args.get(0));
    return value.setScale(0, RoundingMode.CEILING);
  }

  private Object floor(List<Object> args) {
    if (args.size() != 1) {
      throw new IllegalArgumentException("floor函数需要1个参数");
    }

    BigDecimal value = CalculatorUtils.toBigDecimal(args.get(0));
    return value.setScale(0, RoundingMode.FLOOR);
  }

  private Object round(List<Object> args) {
    if (args.size() < 1 || args.size() > 2) {
      throw new IllegalArgumentException("round函数需要1-2个参数");
    }

    BigDecimal value = CalculatorUtils.toBigDecimal(args.get(0));
    if (args.size() == 1) {
      return value.setScale(0, RoundingMode.HALF_UP);
    } else {
      BigDecimal value2 = CalculatorUtils.toBigDecimal(args.get(0));
      int digits = CalculatorUtils.toBigDecimal(args.get(1)).intValue();
      return value2.setScale(digits, RoundingMode.HALF_UP);
    }
  }

  private Object sin(List<Object> args) {
    if (args.size() != 1) {
      throw new IllegalArgumentException("sin函数需要1个参数");
    }

    BigDecimal value = CalculatorUtils.toBigDecimal(args.get(0));
    double result = Math.sin(value.doubleValue());
    return new BigDecimal(result, CalculatorUtils.MATH_CONTEXT);
  }

  private Object cos(List<Object> args) {
    if (args.size() != 1) {
      throw new IllegalArgumentException("cos函数需要1个参数");
    }

    BigDecimal value = CalculatorUtils.toBigDecimal(args.get(0));
    double result = Math.cos(value.doubleValue());
    return new BigDecimal(result, CalculatorUtils.MATH_CONTEXT);
  }

  private Object tan(List<Object> args) {
    if (args.size() != 1) {
      throw new IllegalArgumentException("tan函数需要1个参数");
    }

    BigDecimal value = CalculatorUtils.toBigDecimal(args.get(0));
    double result = Math.tan(value.doubleValue());
    return new BigDecimal(result, CalculatorUtils.MATH_CONTEXT);
  }

  private Object asin(List<Object> args) {
    if (args.size() != 1) {
      throw new IllegalArgumentException("asin函数需要1个参数");
    }

    BigDecimal value = CalculatorUtils.toBigDecimal(args.get(0));
    double result = Math.asin(value.doubleValue());
    return new BigDecimal(result, CalculatorUtils.MATH_CONTEXT);
  }

  private Object acos(List<Object> args) {
    if (args.size() != 1) {
      throw new IllegalArgumentException("acos函数需要1个参数");
    }

    BigDecimal value = CalculatorUtils.toBigDecimal(args.get(0));
    double result = Math.acos(value.doubleValue());
    return new BigDecimal(result, CalculatorUtils.MATH_CONTEXT);
  }

  private Object atan(List<Object> args) {
    if (args.size() != 1) {
      throw new IllegalArgumentException("atan函数需要1个参数");
    }

    BigDecimal value = CalculatorUtils.toBigDecimal(args.get(0));
    double result = Math.atan(value.doubleValue());
    return new BigDecimal(result, CalculatorUtils.MATH_CONTEXT);
  }

  private Object sinh(List<Object> args) {
    if (args.size() != 1) {
      throw new IllegalArgumentException("sinh函数需要1个参数");
    }

    BigDecimal value = CalculatorUtils.toBigDecimal(args.get(0));
    double result = Math.sinh(value.doubleValue());
    return new BigDecimal(result, CalculatorUtils.MATH_CONTEXT);
  }

  private Object cosh(List<Object> args) {
    if (args.size() != 1) {
      throw new IllegalArgumentException("cosh函数需要1个参数");
    }

    BigDecimal value = CalculatorUtils.toBigDecimal(args.get(0));
    double result = Math.cosh(value.doubleValue());
    return new BigDecimal(result, CalculatorUtils.MATH_CONTEXT);
  }

  private Object tanh(List<Object> args) {
    if (args.size() != 1) {
      throw new IllegalArgumentException("tanh函数需要1个参数");
    }

    BigDecimal value = CalculatorUtils.toBigDecimal(args.get(0));
    double result = Math.tanh(value.doubleValue());
    return new BigDecimal(result, CalculatorUtils.MATH_CONTEXT);
  }

  private Object log(List<Object> args) {
    if (args.size() != 1) {
      throw new IllegalArgumentException("log函数需要1个参数");
    }

    BigDecimal value = CalculatorUtils.toBigDecimal(args.get(0));
    if (value.compareTo(BigDecimal.ZERO) <= 0) {
      throw new ArithmeticException("log函数的参数必须大于0");
    }

    double result = Math.log(value.doubleValue());
    return new BigDecimal(result, CalculatorUtils.MATH_CONTEXT);
  }

  private Object log10(List<Object> args) {
    if (args.size() != 1) {
      throw new IllegalArgumentException("log10函数需要1个参数");
    }

    BigDecimal value = CalculatorUtils.toBigDecimal(args.get(0));
    if (value.compareTo(BigDecimal.ZERO) <= 0) {
      throw new ArithmeticException("log10函数的参数必须大于0");
    }

    double result = Math.log10(value.doubleValue());
    return new BigDecimal(result, CalculatorUtils.MATH_CONTEXT);
  }

  private Object exp(List<Object> args) {
    if (args.size() != 1) {
      throw new IllegalArgumentException("exp函数需要1个参数");
    }

    BigDecimal value = CalculatorUtils.toBigDecimal(args.get(0));
    double result = Math.exp(value.doubleValue());
    return new BigDecimal(result, CalculatorUtils.MATH_CONTEXT);
  }

  private Object pow(List<Object> args) {
    if (args.size() != 2) {
      throw new IllegalArgumentException("pow函数需要2个参数");
    }

    BigDecimal base = CalculatorUtils.toBigDecimal(args.get(0));
    BigDecimal exponent = CalculatorUtils.toBigDecimal(args.get(1));
    double result = Math.pow(base.doubleValue(), exponent.doubleValue());
    return new BigDecimal(result, CalculatorUtils.MATH_CONTEXT);
  }

  private Object min(List<Object> args) {
    if (args.isEmpty()) {
      throw new IllegalArgumentException("min函数至少需要1个参数");
    }

    BigDecimal result = CalculatorUtils.toBigDecimal(args.get(0));
    for (int i = 1; i < args.size(); i++) {
      BigDecimal value = CalculatorUtils.toBigDecimal(args.get(i));
      if (value.compareTo(result) < 0) {
        result = value;
      }
    }
    return result;
  }

  private Object max(List<Object> args) {
    if (args.isEmpty()) {
      throw new IllegalArgumentException("max函数至少需要1个参数");
    }

    BigDecimal result = CalculatorUtils.toBigDecimal(args.get(0));
    for (int i = 1; i < args.size(); i++) {
      BigDecimal value = CalculatorUtils.toBigDecimal(args.get(i));
      if (value.compareTo(result) > 0) {
        result = value;
      }
    }
    return result;
  }

  private Object avg(List<Object> args) {
    if (args.isEmpty()) {
      throw new IllegalArgumentException("avg函数至少需要1个参数");
    }

    BigDecimal sum = BigDecimal.ZERO;
    for (Object arg : args) {
      sum = sum.add(CalculatorUtils.toBigDecimal(arg));
    }

    return sum.divide(new BigDecimal(args.size()), CalculatorUtils.MATH_CONTEXT);
  }

  /**
   * 逻辑与函数
   * 
   * 所有参数都为真时返回真，否则返回假
   * 
   * @param args 参数列表
   * @return 逻辑与结果
   */
  private Object and(List<Object> args) {
    for (Object arg : args) {
      if (!isTrue(arg)) {
        return Boolean.FALSE;
      }
    }
    return Boolean.TRUE;
  }

  /**
   * 逻辑或函数
   * 
   * 任一参数为真时返回真，否则返回假
   * 
   * @param args 参数列表
   * @return 逻辑或结果
   */
  private Object or(List<Object> args) {
    for (Object arg : args) {
      if (isTrue(arg)) {
        return Boolean.TRUE;
      }
    }
    return Boolean.FALSE;
  }

  /**
   * 逻辑非函数
   * 
   * @param args 参数列表，应包含1个参数
   * @return 逻辑非结果
   */
  private Object not(List<Object> args) {
    if (args.size() != 1) {
      throw new RuntimeException("not 函数需要 1 个参数");
    }
    return isTrue(args.get(0)) ? Boolean.FALSE : Boolean.TRUE;
  }

  /**
   * 逻辑异或函数
   * 
   * 奇数个参数为真时返回真，否则返回假
   * 
   * @param args 参数列表
   * @return 逻辑异或结果
   */
  private Object xor(List<Object> args) {
    int trueCount = 0;
    for (Object arg : args) {
      if (isTrue(arg)) {
        trueCount++;
      }
    }
    return (trueCount % 2 == 1) ? Boolean.TRUE : Boolean.FALSE;
  }

  /**
   * 条件函数（if）
   * 
   * @param args 参数列表，应包含3个参数（条件、真值、假值）
   * @return 根据条件返回真值或假值
   */
  private Object ifFunction(List<Object> args) {
    if (args.size() != 3) {
      throw new RuntimeException("if 函数需要 3 个参数");
    }
    return isTrue(args.get(0)) ? args.get(1) : args.get(2);
  }

  /**
   * 多条件函数（ifs）
   * 
   * 按顺序检查条件-值对，返回第一个满足条件的值
   * 如果参数个数为奇数，最后一个参数作为默认值
   * 
   * @param args 参数列表，至少包含2个参数
   * @return 匹配条件的值或默认值
   */
  private Object ifs(List<Object> args) {
    if (args.size() < 2) {
      throw new RuntimeException("ifs 函数至少需要 2 个参数");
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

    throw new RuntimeException("ifs 函数没有匹配的条件且没有默认值");
  }

  /**
   * Java方法调用函数
   * 
   * 通过反射调用Java类的静态方法
   * 
   * @param args 参数列表，至少包含2个参数（类名、方法名、方法参数...）
   * @return 方法调用结果
   */
  private Object jcall(List<Object> args) {
    if (args.size() < 2) {
      throw new RuntimeException("jcall 函数至少需要 2 个参数（类名和方法名）");
    }

    // 确保类名和方法名是字符串
    String className = args.get(0) instanceof String ? (String) args.get(0) : args.get(0).toString();
    String methodName = args.get(1) instanceof String ? (String) args.get(1) : args.get(1).toString();
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

      throw new RuntimeException("找不到匹配的静态方法 " + className + "." + methodName +
          " 参数个数: " + methodArgs.length);

    } catch (Exception e) {
      throw new RuntimeException("Java方法调用失败 " + className + "." + methodName +
          " - " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
    }
  }

  // ==================== 辅助方法 ====================



  /**
   * 转换Java方法调用的参数类型
   * 
   * @param arg 原始参数
   * @param targetType 目标类型
   * @return 转换后的参数
   */
  private Object convertArgument(Object arg, Class<?> targetType) {
    if (targetType.isAssignableFrom(arg.getClass())) {
      return arg;
    }

    if (targetType == int.class || targetType == Integer.class) {
      return CalculatorUtils.toBigDecimal(arg).intValue();
    }
    if (targetType == long.class || targetType == Long.class) {
      return CalculatorUtils.toBigDecimal(arg).longValue();
    }
    if (targetType == float.class || targetType == Float.class) {
      return CalculatorUtils.toBigDecimal(arg).floatValue();
    }
    if (targetType == double.class || targetType == Double.class) {
      return CalculatorUtils.toBigDecimal(arg).doubleValue();
    }
    if (targetType == boolean.class || targetType == Boolean.class) {
      return isTrue(arg);
    }
    if (targetType == String.class) {
      return arg.toString();
    }

    return arg;
  }

  /**
   * 转换Java方法调用的返回值
   * 
   * @param result 原始返回值
   * @return 转换后的返回值
   */
  private Object convertResult(Object result) {
    if (result instanceof Number && !(result instanceof BigDecimal)) {
      return new BigDecimal(result.toString(), CalculatorUtils.MATH_CONTEXT);
    }
    return result;
  }
}
