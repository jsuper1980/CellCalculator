package j2.basic.utils.calc;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/**
 * 单元格类，表示计算引擎中的一个单元格
 * 包含单元格的标识、内容、计算结果、错误信息和依赖关系
 */
public class Cell {
  private String id; // 单元格标识，如"A1"，单元格标识由字母、字符（英文字母、希腊字母、中文）、下划线或数字组成，不能以数字开头，不能有空格，不能是保留字
  private String define; // 单元格定义，如"1"，"=A2+1"，"Hello world"，"'你好'"
  private Object value; // 单元格赋值或计算结果，如1，11，Hello world，你好，若赋值或计算错误则为 null，若赋值或计算结果为数字则为 BigDecimal 类型，若为字符串则为 String 类型，若为逻辑则为 Boolean 类型
  private String error; // 赋值或计算错误时的错误信息, 若赋值或计算正确则为 null
  private Set<String> dependencies; // 单元格依赖的其他单元格，如{"A2"}

  /**
   * 构造函数
   * 
   * @param id 单元格标识
   */
  public Cell(String id) {
    this.id = id;
    this.define = "";
    this.value = null;
    this.error = null;
    this.dependencies = new HashSet<>();
  }

  /**
   * 获取单元格标识
   * 
   * @return 单元格标识
   */
  public String getId() {
    return id;
  }

  /**
   * 获取单元格内容
   * 
   * @return 单元格内容
   */
  public String getDefine() {
    return define;
  }

  /**
   * 设置单元格定义
   * 
   * @param define 单元格定义
   */
  public void setDefine(String define) {
    this.define = define;
  }

  /**
   * 获取单元格计算结果
   * 
   * @return 单元格计算结果
   */
  public Object getValue() {
    return value;
  }

  /**
   * 设置单元格计算结果
   * 
   * @param value 单元格赋值或计算结果
   */
  public void setValue(Object value) {
    this.value = value;
  }

  /**
   * 获取错误信息
   * 
   * @return 错误信息
   */
  public String getError() {
    return error;
  }

  /**
   * 设置错误信息
   * 
   * @param errorString 错误信息
   */
  public void setError(String errorString) {
    this.error = errorString;
  }

  /**
   * 获取依赖关系（返回副本以保证封装性）
   * 
   * @return 依赖关系集合
   */
  public Set<String> getDependencies() {
    return dependencies != null ? new HashSet<>(dependencies) : new HashSet<>();
  }

  /**
   * 设置依赖关系
   * 
   * @param dependencies 依赖关系集合
   */
  public void setDependencies(Set<String> dependencies) {
    this.dependencies = dependencies != null ? new HashSet<>(dependencies) : new HashSet<>();
  }

  /**
   * 判断单元格是否计算错误
   * 
   * @return 如果单元格计算错误则返回 true，否则返回 false
   */
  public boolean hasError() {
    return error != null;
  }

  /**
   * 获取单元格值的类型
   * 
   * @return 单元格值的类型，如 "null"、"number"、"string"、"boolean" 等
   */
  public String getValueType() {
    if (value == null) {
      return "null";
    }
    if (value instanceof BigDecimal) {
      return "number";
    }
    if (value instanceof String) {
      return "string";
    }
    if (value instanceof Boolean) {
      return "boolean";
    }
    return value.getClass().getSimpleName();
  }
}
