package j2.basic.utils.calc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * CellCalculator的save、load和recalculate方法测试类
 * 
 * @author j2
 * @version 1.0
 */
public class CellCalculatorSaveLoadTest {

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

  /**
   * 测试save方法 - 基本功能
   */
  @Test
  void testSaveBasic() throws IOException {
    // 设置一些测试数据
    calculator.set("A1", "10");
    calculator.set("A2", "20");
    calculator.set("A3", "=A1+A2");
    calculator.set("B1", "'Hello World'");

    // 保存到输出流
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    calculator.save(outputStream);

    // 验证保存的内容
    String savedContent = outputStream.toString("UTF-8");
    System.out.println("保存的内容:");
    System.out.println(savedContent);

    // 验证包含所有单元格定义
    assertTrue(savedContent.contains("A1:10"));
    assertTrue(savedContent.contains("A2:20"));
    assertTrue(savedContent.contains("A3:=A1+A2"));
    assertTrue(savedContent.contains("B1:'Hello World'"));
  }

  /**
   * 测试save方法 - 空单元格不保存
   */
  @Test
  void testSaveEmptyCells() throws IOException {
    // 设置一些测试数据，包括空单元格
    calculator.set("A1", "10");
    calculator.set("A2", ""); // 空内容
    calculator.set("A3", "20");

    // 保存到输出流
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    calculator.save(outputStream);

    // 验证保存的内容
    String savedContent = outputStream.toString("UTF-8");
    System.out.println("保存的内容（空单元格测试）:");
    System.out.println(savedContent);

    // 验证只包含非空单元格
    assertTrue(savedContent.contains("A1:10"));
    assertFalse(savedContent.contains("A2:")); // 空单元格不应该被保存
    assertTrue(savedContent.contains("A3:20"));
  }

  /**
   * 测试load方法 - 基本功能
   */
  @Test
  void testLoadBasic() throws IOException {
    // 准备测试数据
    String testData = "A1:10\nA2:20\nA3:=A1+A2\nB1:'Hello World'\n";
    ByteArrayInputStream inputStream = new ByteArrayInputStream(testData.getBytes("UTF-8"));

    // 载入数据
    calculator.load(inputStream);

    // 验证单元格定义被正确载入
    assertEquals("10", calculator.getDefine("A1"));
    assertEquals("20", calculator.getDefine("A2"));
    assertEquals("=A1+A2", calculator.getDefine("A3"));
    assertEquals("'Hello World'", calculator.getDefine("B1"));

    // 注意：load后单元格值应该为null，等待recalculate
    assertNull(calculator.getRawValue("A1"));
    assertNull(calculator.getRawValue("A2"));
    assertNull(calculator.getRawValue("A3"));
    assertNull(calculator.getRawValue("B1"));
  }

  /**
   * 测试load方法 - 格式错误处理
   */
  @Test
  void testLoadInvalidFormat() {
    // 测试缺少冒号的情况
    String invalidData = "A1:10\nA2_invalid_format\nA3:20\n";
    ByteArrayInputStream inputStream = new ByteArrayInputStream(invalidData.getBytes());

    // 应该抛出IllegalArgumentException
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      calculator.load(inputStream);
    });

    assertTrue(exception.getMessage().contains("格式错误"));
    assertTrue(exception.getMessage().contains("冒号分隔符"));
  }

  /**
   * 测试load方法 - 无效单元格ID处理
   */
  @Test
  void testLoadInvalidCellId() {
    // 测试无效的单元格ID
    String invalidData = "A1:10\n123invalid:20\nA3:30\n";
    ByteArrayInputStream inputStream = new ByteArrayInputStream(invalidData.getBytes());

    // 应该抛出IllegalArgumentException
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      calculator.load(inputStream);
    });

    assertTrue(exception.getMessage().contains("无效的单元格ID"));
  }

  /**
   * 测试recalculate方法 - 基本功能
   */
  @Test
  void testRecalculateBasic() throws IOException {
    // 先通过load载入数据（不触发计算）
    String testData = "A1:10\nA2:20\nA3:=A1+A2\nB1:'Hello World'\n";
    ByteArrayInputStream inputStream = new ByteArrayInputStream(testData.getBytes("UTF-8"));
    calculator.load(inputStream);

    // 验证载入后值为null
    assertNull(calculator.getRawValue("A1"));
    assertNull(calculator.getRawValue("A2"));
    assertNull(calculator.getRawValue("A3"));
    assertNull(calculator.getRawValue("B1"));

    // 执行重算
    calculator.recalculate();

    // 验证重算后的值
    assertEquals(new BigDecimal("10"), calculator.getRawValue("A1"));
    assertEquals(new BigDecimal("20"), calculator.getRawValue("A2"));
    assertEquals(new BigDecimal("30"), calculator.getRawValue("A3")); // A1+A2=30
    assertEquals("Hello World", calculator.getRawValue("B1"));
  }

  /**
   * 测试recalculate方法 - 复杂依赖关系
   */
  @Test
  void testRecalculateComplexDependencies() throws IOException {
    // 创建复杂的依赖关系
    String testData = "A1:10\nA2:=A1*2\nA3:=A2+5\nA4:=A1+A3\nB1:=A4*2\n";
    ByteArrayInputStream inputStream = new ByteArrayInputStream(testData.getBytes("UTF-8"));
    calculator.clear();
    calculator.load(inputStream);

    // 执行重算
    calculator.recalculate();

    // 验证计算结果
    // A1 = 10
    // A2 = A1*2 = 20
    // A3 = A2+5 = 25
    // A4 = A1+A3 = 35
    // B1 = A4*2 = 70
    assertEquals(new BigDecimal("10"), calculator.getRawValue("A1"));
    assertEquals(new BigDecimal("20"), calculator.getRawValue("A2"));
    assertEquals(new BigDecimal("25"), calculator.getRawValue("A3"));
    assertEquals(new BigDecimal("35"), calculator.getRawValue("A4"));
    assertEquals(new BigDecimal("70"), calculator.getRawValue("B1"));
  }

  /**
   * 综合测试 - save-load-recalculate完整流程
   */
  @Test
  void testSaveLoadRecalculateWorkflow() throws IOException {
    // 第一步：创建原始数据
    calculator.set("X1", "100");
    calculator.set("X2", "200");
    calculator.set("X3", "=X1+X2");
    calculator.set("Y1", "=X3*2");
    calculator.set("Z1", "'测试字符串'");

    // 验证原始计算结果
    assertEquals(new BigDecimal("300"), calculator.getRawValue("X3"));
    assertEquals(new BigDecimal("600"), calculator.getRawValue("Y1"));
    assertEquals("测试字符串", calculator.getRawValue("Z1"));

    // 第二步：保存数据
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    calculator.save(outputStream);
    String savedData = outputStream.toString("UTF-8");
    System.out.println("保存的数据:");
    System.out.println(savedData);

    // 清空计算器数据
    calculator.clear();

    // 第三步：创建新的计算器实例并载入数据
    CellCalculator newCalculator = new CellCalculator();
    try {
      ByteArrayInputStream inputStream = new ByteArrayInputStream(savedData.getBytes("UTF-8"));
      newCalculator.load(inputStream);

      // 验证载入后定义正确但值为null
      assertEquals("100", newCalculator.getDefine("X1"));
      assertEquals("200", newCalculator.getDefine("X2"));
      assertEquals("=X1+X2", newCalculator.getDefine("X3"));
      assertEquals("=X3*2", newCalculator.getDefine("Y1"));
      assertEquals("'测试字符串'", newCalculator.getDefine("Z1"));

      assertNull(newCalculator.getRawValue("X1"));
      assertNull(newCalculator.getRawValue("X2"));
      assertNull(newCalculator.getRawValue("X3"));
      assertNull(newCalculator.getRawValue("Y1"));
      assertNull(newCalculator.getRawValue("Z1"));

      // 第四步：重新计算
      newCalculator.recalculate();

      // 第五步：验证重算后的结果与原始结果一致
      assertEquals(new BigDecimal("100"), newCalculator.getRawValue("X1"));
      assertEquals(new BigDecimal("200"), newCalculator.getRawValue("X2"));
      assertEquals(new BigDecimal("300"), newCalculator.getRawValue("X3"));
      assertEquals(new BigDecimal("600"), newCalculator.getRawValue("Y1"));
      assertEquals("测试字符串", newCalculator.getRawValue("Z1"));

      System.out.println("综合测试通过：save-load-recalculate流程正常工作");

    } finally {
      newCalculator.shutdown();
    }
  }

  /**
   * 测试空行处理
   */
  @Test
  void testLoadWithEmptyLines() throws IOException {
    // 包含空行的测试数据
    String testData = "A1:10\n\nA2:20\n\n\nA3:=A1+A2\n\n";
    ByteArrayInputStream inputStream = new ByteArrayInputStream(testData.getBytes("UTF-8"));

    calculator.load(inputStream);
    calculator.recalculate();

    // 验证空行被正确忽略
    assertEquals(new BigDecimal("10"), calculator.getRawValue("A1"));
    assertEquals(new BigDecimal("20"), calculator.getRawValue("A2"));
    assertEquals(new BigDecimal("30"), calculator.getRawValue("A3"));
  }

  /**
   * 测试包含冒号的单元格内容
   */
  @Test
  void testLoadWithColonInContent() throws IOException {
    // 单元格内容包含冒号的情况
    String testData = "A1:10\nB1:'时间: 12:30:45'\nC1:=A1*2\n";
    ByteArrayInputStream inputStream = new ByteArrayInputStream(testData.getBytes("UTF-8"));

    calculator.load(inputStream);
    calculator.recalculate();

    assertEquals(new BigDecimal("10"), calculator.getRawValue("A1"));
    assertEquals("时间: 12:30:45", calculator.getRawValue("B1"));
    assertEquals(new BigDecimal("20"), calculator.getRawValue("C1"));
  }
}
