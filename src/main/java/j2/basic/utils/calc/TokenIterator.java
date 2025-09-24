package j2.basic.utils.calc;

import java.util.List;

/**
 * Token迭代器，用于表达式解析过程中遍历token列表
 */
public class TokenIterator {
    private final List<String> tokens;
    private int index = 0;
    
    /**
     * 构造函数
     * @param tokens token列表
     */
    public TokenIterator(List<String> tokens) {
        this.tokens = tokens;
    }
    
    /**
     * 检查是否还有下一个token
     * @return 如果还有下一个token返回true，否则返回false
     */
    public boolean hasNext() {
        return index < tokens.size();
    }
    
    /**
     * 获取下一个token并移动指针
     * @return 下一个token
     */
    public String next() {
        return tokens.get(index++);
    }
    
    /**
     * 查看下一个token但不移动指针
     * @return 下一个token，如果没有则返回null
     */
    public String peek() {
        return hasNext() ? tokens.get(index) : null;
    }
}