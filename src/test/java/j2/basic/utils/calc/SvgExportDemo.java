package j2.basic.utils.calc;

import java.io.FileOutputStream;
import java.io.IOException;

/**
 * SVG导出功能演示
 * 
 * 这个演示展示了如何使用CellCalculatorSvgExporter将表格数据导出为美观的SVG格式
 */
public class SvgExportDemo {

  public static void main(String[] args) {
    CellCalculator calculator = new CellCalculator();

    try {
      // 创建一个复杂的表格示例
      System.out.println("创建表格数据...");

      // 基础数据
      calculator.set("A1", "100");
      calculator.set("A2", "200");
      calculator.set("A3", "300");

      // 计算列
      calculator.set("B1", "=A1*1.2");
      calculator.set("B2", "=A2*1.5");
      calculator.set("B3", "=A3*0.8");

      // 汇总列
      calculator.set("C1", "=A1+B1");
      calculator.set("C2", "=A2+B2");
      calculator.set("C3", "=A3+B3");

      // 总计行
      calculator.set("D1", "=C1+C2+C3");
      calculator.set("D2", "=B1+B2+B3");

      // 字符串和复杂公式
      calculator.set("E1", "'总销售额'");
      calculator.set("E2", "=D1*1.1");
      calculator.set("E3", "=IF(E2>1000, '优秀', '良好')");

      System.out.println("表格数据创建完成！");

      // 创建SVG导出器
      SvgExporter exporter = new SvgExporter(calculator);

      // 导出为SVG文件
      String fileName = "demo.svg";
      try (FileOutputStream fileOutput = new FileOutputStream(fileName)) {
        exporter.export(fileOutput);
        System.out.println("SVG文件已导出: " + fileName);
        System.out.println("请用浏览器打开该文件查看可视化的单元格依赖关系图");
      }

      // 显示一些计算结果
      System.out.println("\n计算结果示例:");
      System.out.println("A1 = " + calculator.getRawValue("A1"));
      System.out.println("B1 = " + calculator.getRawValue("B1"));
      System.out.println("C1 = " + calculator.getRawValue("C1"));
      System.out.println("D1 = " + calculator.getRawValue("D1"));
      System.out.println("E2 = " + calculator.getRawValue("E2"));
      System.out.println("E3 = " + calculator.getRawValue("E3"));

    } catch (IOException e) {
      System.err.println("导出SVG时发生错误: " + e.getMessage());
      e.printStackTrace();
    } finally {
      calculator.shutdown();
    }
  }
}
