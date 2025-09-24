package j2.basic.utils.calc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CellCalculator测试类
 * 验证CellCalculator的各项功能是否符合README文档要求
 */
@DisplayName("CellCalculator 功能测试")
public class CellCalculatorTests {

  private CellCalculator calculator;

  @BeforeEach
  void setUp() {
    calculator = new CellCalculator();
  }

  @AfterEach
  void tearDown() {
    if (calculator != null) {
      calculator.shutdown();
    }
  }

  @Test
  @DisplayName("基本操作测试")
  void testBasicOperations() {
    // 设置数值
    calculator.set("A1", "100");
    calculator.set("B1", 200.5);
    calculator.set("C1", true);
    calculator.set("D1", "Hello World");
    calculator.set("E1", "'你好'");

    System.out.println("A1 = " + calculator.get("A1"));
    System.out.println("B1 = " + calculator.get("B1"));
    System.out.println("C1 = " + calculator.get("C1"));
    System.out.println("D1 = " + calculator.get("D1"));
    System.out.println("E1 = " + calculator.get("E1"));

    // 获取并验证
    assertEquals("100", calculator.get("A1"));
    assertEquals("number", calculator.getType("A1"));

    assertEquals("200.5", calculator.get("B1"));
    assertEquals("number", calculator.getType("B1"));

    assertEquals("TRUE", calculator.get("C1"));
    assertEquals("boolean", calculator.getType("C1"));

    assertEquals("Hello World", calculator.get("D1"));
    assertEquals("string", calculator.getType("D1"));

    assertEquals("你好", calculator.get("E1"));
    assertEquals("string", calculator.getType("E1"));
  }

  @Test
  @DisplayName("数学表达式测试")
  void testMathematicalExpressions() {
    // 先设置基础数据
    calculator.set("A1", "100");
    calculator.set("B1", "200.5");

    calculator.set("E1", "=A1+B1");
    calculator.set("E2", "=A1*B1");
    calculator.set("E3", "=B1/A1");
    calculator.set("E4", "=A1^2");
    calculator.set("E5", "=A1%7");

    System.out.println("A1 = " + calculator.get("A1"));
    System.out.println("B1 = " + calculator.get("B1"));
    System.out.println("E1 = A1+B1 = " + calculator.get("E1"));
    System.out.println("E2 = A1*B1 = " + calculator.get("E2"));
    System.out.println("E3 = B1/A1 = " + calculator.get("E3"));
    System.out.println("E4 = A1^2 = " + calculator.get("E4"));
    System.out.println("E5 = A1%7 = " + calculator.get("E5"));

    assertEquals("300.5", calculator.get("E1"));
    assertEquals("20050", calculator.get("E2"));
    assertEquals("2.005", calculator.get("E3"));
    assertEquals("10000", calculator.get("E4"));
    assertEquals("2", calculator.get("E5"));
  }

  @Test
  @DisplayName("逻辑表达式测试")
  void testLogicalExpressions() {
    // 先设置基础数据
    calculator.set("A1", "100");
    calculator.set("B1", "200.5");
    calculator.set("C1", "true");

    calculator.set("F1", "=A1>50");
    calculator.set("F2", "=A1==100");
    calculator.set("F3", "=C1&&true");
    calculator.set("F4", "=A1>50&&B1<300");
    calculator.set("F5", "=NOT(TRUE)");
    calculator.set("F6", "=XOR(TRUE,FALSE,TRUE)");
    calculator.set("F7", "=AND(TRUE,TRUE,TRUE)");
    calculator.set("F8", "=OR(TRUE,FALSE,TRUE)");


    System.out.println("A1 = " + calculator.get("A1"));
    System.out.println("B1 = " + calculator.get("B1"));
    System.out.println("C1 = " + calculator.get("C1"));
    System.out.println("F1 = A1>50 = " + calculator.get("F1"));
    System.out.println("F2 = A1==100 = " + calculator.get("F2"));
    System.out.println("F3 = C1&&true = " + calculator.get("F3"));
    System.out.println("F4 = A1>50&&B1<300 = " + calculator.get("F4"));
    System.out.println("F5 = NOT(TRUE) = " + calculator.get("F5"));
    System.out.println("F6 = XOR(TRUE,FALSE,TRUE) = " + calculator.get("F6"));
    System.out.println("F7 = AND(TRUE,TRUE,TRUE) = " + calculator.get("F7"));
    System.out.println("F8 = OR(TRUE,FALSE,TRUE) = " + calculator.get("F8"));

    assertEquals("TRUE", calculator.get("F1"));
    assertEquals("TRUE", calculator.get("F2"));
    assertEquals("TRUE", calculator.get("F3"));
    assertEquals("TRUE", calculator.get("F4"));
    assertEquals("FALSE", calculator.get("F5"));
    assertEquals("FALSE", calculator.get("F6"));
    assertEquals("TRUE", calculator.get("F7"));
    assertEquals("TRUE", calculator.get("F8"));
  }

  @Test
  @DisplayName("内置函数测试")
  void testBuiltinFunctions() {
    // 先设置基础数据
    calculator.set("A1", "100");
    calculator.set("B1", "200.5");

    calculator.set("G1", "=ABS(-50)");
    calculator.set("G2", "=MAX(A1,B1)");
    calculator.set("G3", "=MIN(A1,B1)");
    calculator.set("G4", "=SQRT(A1)");
    calculator.set("G5", "=ROUND(B1,1)");
    calculator.set("G6", "=IF(A1>50,'大,big','小 and small')");

    calculator.set("C1", 55);
    calculator.set("G7", "=IFS(C1>=90,'优',C1>=80,'良',C1>=60,'中','差')");

    System.out.println("A1 = " + calculator.get("A1"));
    System.out.println("B1 = " + calculator.get("B1"));
    System.out.println("G1 = ABS(-50) = " + calculator.get("G1"));
    System.out.println("G2 = MAX(A1,B1) = " + calculator.get("G2"));
    System.out.println("G3 = MIN(A1,B1) = " + calculator.get("G3"));
    System.out.println("G4 = SQRT(A1) = " + calculator.get("G4"));
    System.out.println("G5 = ROUND(B1,1) = " + calculator.get("G5"));
    System.out.println("G6 = IF(A1>50,'大,big','小,small') = " + calculator.get("G6"));
    System.out.println("G7 = " + calculator.getDefine("G7") + " = " + calculator.get("G7"));

    assertEquals("50", calculator.get("G1"));
    assertEquals("200.5", calculator.get("G2"));
    assertEquals("100", calculator.get("G3"));
    assertEquals("10", calculator.get("G4"));
    assertEquals("200.5", calculator.get("G5"));
    assertEquals("大,big", calculator.get("G6"));
    assertEquals("差", calculator.get("G7"));
  }

  @Test
  @DisplayName("依赖关系和联动计算测试")
  void testDependencyCalculation() {
    // 先设置基础数据
    calculator.set("A1", "100");

    calculator.set("H1", "=A1+10");
    calculator.set("H2", "=H1*2");
    calculator.set("H3", "=H2+H1");

    System.out.println("A1 = " + calculator.get("A1"));
    System.out.println("H1 = A1+10 = " + calculator.get("H1"));
    System.out.println("H2 = H1*2 = " + calculator.get("H2"));
    System.out.println("H3 = H2+H1 = " + calculator.get("H3"));

    // 验证初始值
    assertEquals("110", calculator.get("H1"));
    assertEquals("220", calculator.get("H2"));
    assertEquals("330", calculator.get("H3"));

    // 修改A1的值，观察联动效果
    calculator.set("A1", "150");

    System.out.println("修改 A1 = " + calculator.get("A1"));
    System.out.println("H1 = A1+10 = " + calculator.get("H1"));
    System.out.println("H2 = H1*2 = " + calculator.get("H2"));
    System.out.println("H3 = H2+H1 = " + calculator.get("H3"));

    // 验证联动计算结果
    assertEquals("160", calculator.get("H1"));
    assertEquals("320", calculator.get("H2"));
    assertEquals("480", calculator.get("H3"));
  }

  @Test
  @DisplayName("错误处理测试")
  void testErrorHandling() {
    // 先设置基础数据
    calculator.set("A1", "100");

    // 除零错误
    calculator.set("J1", "=A1/0");
    String j1Error = calculator.getError("J1");

    System.out.println("J1 = A1/0 = " + calculator.get("J1"));
    System.out.println("J1 error = " + j1Error);
    assertNotNull(j1Error, "除零应该产生错误");
    assertNull(calculator.get("J1"), "有错误的单元格应该返回null");

    // 无效函数
    calculator.set("J2", "=INVALID_FUNC(A1)");
    String j2Error = calculator.getError("J2");

    System.out.println("J2 = INVALID_FUNC(A1) = " + calculator.get("J2"));
    System.out.println("J2 error = " + j2Error);
    assertNotNull(j2Error, "无效函数应该产生错误");
    assertNull(calculator.get("J2"), "有错误的单元格应该返回null");

    // 无效单元格引用
    calculator.set("J3", "=NONEXISTENT_CELL+1");
    String j3Error = calculator.getError("J3");

    System.out.println("J3 = NONEXISTENT_CELL+1 = " + calculator.get("J3"));
    System.out.println("J3 error = " + j3Error);
    assertNotNull(j3Error, "无效单元格引用应该产生错误");
    assertNull(calculator.get("J3"), "有错误的单元格应该返回null");
  }

  @Test
  @DisplayName("Java方法调用测试")
  void testJavaMethodCall() {
    calculator.set("K1", "=jcall('java.lang.String','valueOf',100)");
    calculator.set("K2", "=jcall('j2.basic.utils.calc.MathUtils','factorial',5)");
    calculator.set("K3", "=jcall('notFoundClass','notFoundMethod',1,2)");

    System.out.println("K1 = jcall('String','valueOf',100) = " + calculator.get("K1"));
    System.out.println("K2 = jcall('j2.basic.utils.calc.MathUtils','factorial',5) = " + calculator.get("K2"));
    System.out.println("K3 = jcall('notFoundClass','notFoundMethod',1,2) = " + calculator.get("K3"));
    System.out.println("K3 error = " + calculator.getError("K3"));

    assertEquals("100", calculator.get("K1"));
    assertEquals("120", calculator.get("K2"));
    assertNotNull(calculator.getError("K3"), "Java方法调用失败应该产生错误");
  }
}
