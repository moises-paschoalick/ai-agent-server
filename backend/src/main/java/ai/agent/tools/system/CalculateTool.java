package ai.agent.tools.system;

import ai.agent.tools.ToolDefinition;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * calculate: arithmetic only, with a small recursive-descent parser over BigDecimal. Nothing but
 * numbers, operators and a fixed list of functions is understood, so no code is ever run.
 *
 * <pre>
 * expr    := term (('+' | '-') term)*
 * term    := unary (('*' | '/') unary)*
 * unary   := ('+' | '-') unary | power
 * power   := primary ('^' unary)?          right associative: 2^3^2 = 2^9
 * primary := number | name '(' expr (',' expr)* ')' | '(' expr ')'
 * </pre>
 */
public class CalculateTool implements SystemTool {

    public static final String NAME = "calculate";

    static final int MAX_LENGTH = 500;
    static final int MAX_DEPTH = 50;
    private static final MathContext MC = MathContext.DECIMAL64;

    private static final ToolDefinition DEFINITION;

    static {
        Map<String, Object> expression = new LinkedHashMap<>();
        expression.put("type", "string");
        expression.put("description", "Arithmetic expression with + - * / ^, parentheses and the functions round(x), "
                + "round(x, digits), floor, ceil, abs, sqrt, min, max. Numbers use a dot for decimals and no thousands "
                + "separator: Brazilian 1.250 (one thousand two hundred fifty) is 1250 and 2,5 is 2.5. Percentages are "
                + "multiplications: 15% of 1250 is 1250*0.15. Example: round((1250*0.15)+32, 2)");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("expression", expression);
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("expression"));
        DEFINITION = new ToolDefinition(NAME,
                "Calculates an arithmetic expression exactly. Use it for any calculation instead of doing it yourself.",
                parameters, false);
    }

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public String execute(String argumentsJson) {
        String expression = SystemToolArguments.parse(argumentsJson).requiredText("expression");
        return format(evaluate(expression));
    }

    /** Value of the expression; IllegalArgumentException or ArithmeticException with the reason. */
    static BigDecimal evaluate(String expression) {
        if (expression.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("expression is longer than " + MAX_LENGTH + " characters");
        }
        Parser parser = new Parser(expression);
        BigDecimal value = parser.expr(0);
        parser.skipSpaces();
        if (!parser.atEnd()) {
            throw parser.error("unexpected '" + parser.peek() + "'");
        }
        return value;
    }

    /** Plain decimal text, at most 16 significant digits, no trailing zeros. */
    static String format(BigDecimal value) {
        BigDecimal rounded = value.round(MC).stripTrailingZeros();
        return rounded.signum() == 0 ? "0" : rounded.toPlainString();
    }

    private static final class Parser {

        private final String text;
        private int pos;

        Parser(String text) {
            this.text = text;
        }

        BigDecimal expr(int depth) {
            if (depth > MAX_DEPTH) {
                throw new IllegalArgumentException("expression is nested too deeply");
            }
            BigDecimal value = term(depth);
            while (true) {
                if (accept('+')) {
                    value = value.add(term(depth), MC);
                } else if (accept('-')) {
                    value = value.subtract(term(depth), MC);
                } else {
                    return value;
                }
            }
        }

        private BigDecimal term(int depth) {
            BigDecimal value = unary(depth);
            while (true) {
                if (accept('*')) {
                    value = value.multiply(unary(depth), MC);
                } else if (accept('/')) {
                    BigDecimal divisor = unary(depth);
                    if (divisor.signum() == 0) {
                        throw new ArithmeticException("division by zero");
                    }
                    value = value.divide(divisor, MC);
                } else {
                    return value;
                }
            }
        }

        private BigDecimal unary(int depth) {
            if (depth > MAX_DEPTH) {
                throw new IllegalArgumentException("expression is nested too deeply");
            }
            if (accept('-')) {
                return unary(depth + 1).negate();
            }
            if (accept('+')) {
                return unary(depth + 1);
            }
            return power(depth);
        }

        private BigDecimal power(int depth) {
            BigDecimal base = primary(depth);
            if (accept('^')) {
                return pow(base, unary(depth + 1));
            }
            return base;
        }

        private BigDecimal primary(int depth) {
            skipSpaces();
            if (atEnd()) {
                throw error("expression ended too early");
            }
            char c = peek();
            if (c == '(') {
                pos++;
                BigDecimal value = expr(depth + 1);
                expect(')');
                return value;
            }
            if (Character.isDigit(c) || c == '.') {
                return number();
            }
            if (Character.isLetter(c)) {
                return function(depth);
            }
            throw error("unexpected '" + c + "'");
        }

        private BigDecimal number() {
            int start = pos;
            while (!atEnd() && (Character.isDigit(peek()) || peek() == '.')) {
                pos++;
            }
            String literal = text.substring(start, pos);
            try {
                return new BigDecimal(literal);
            } catch (NumberFormatException e) {
                throw error("invalid number '" + literal + "'");
            }
        }

        private BigDecimal function(int depth) {
            int start = pos;
            while (!atEnd() && Character.isLetter(peek())) {
                pos++;
            }
            String name = text.substring(start, pos);
            if (!List.of("round", "floor", "ceil", "abs", "sqrt", "min", "max").contains(name)) {
                throw error("unknown function '" + name + "'");
            }
            expect('(');
            List<BigDecimal> args = new ArrayList<>();
            args.add(expr(depth + 1));
            while (accept(',')) {
                args.add(expr(depth + 1));
            }
            expect(')');
            return switch (name) {
                case "round" -> {
                    arity(name, args, 1, 2);
                    int digits = args.size() == 2 ? integer(args.get(1), "round digits") : 0;
                    yield args.getFirst().setScale(digits, RoundingMode.HALF_UP);
                }
                case "floor" -> one(name, args).setScale(0, RoundingMode.FLOOR);
                case "ceil" -> one(name, args).setScale(0, RoundingMode.CEILING);
                case "abs" -> one(name, args).abs();
                case "sqrt" -> {
                    BigDecimal x = one(name, args);
                    if (x.signum() < 0) {
                        throw new ArithmeticException("square root of a negative number");
                    }
                    yield x.sqrt(MC);
                }
                case "min" -> args.stream().reduce(BigDecimal::min).orElseThrow();
                default -> args.stream().reduce(BigDecimal::max).orElseThrow();
            };
        }

        private BigDecimal pow(BigDecimal base, BigDecimal exponent) {
            if (exponent.stripTrailingZeros().scale() <= 0 && exponent.abs().compareTo(BigDecimal.valueOf(999)) <= 0) {
                int n = exponent.intValueExact();
                if (n < 0 && base.signum() == 0) {
                    throw new ArithmeticException("division by zero");
                }
                return base.pow(n, MC);
            }
            double result = Math.pow(base.doubleValue(), exponent.doubleValue());
            if (Double.isNaN(result) || Double.isInfinite(result)) {
                throw new ArithmeticException("result is not a real number");
            }
            return new BigDecimal(result, MC);
        }

        private static BigDecimal one(String name, List<BigDecimal> args) {
            arity(name, args, 1, 1);
            return args.getFirst();
        }

        private static void arity(String name, List<BigDecimal> args, int min, int max) {
            if (args.size() < min || args.size() > max) {
                throw new IllegalArgumentException(name + " takes " + (min == max ? min : min + " or " + max) + " argument(s)");
            }
        }

        private static int integer(BigDecimal value, String what) {
            try {
                return value.intValueExact();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException(what + " must be an integer");
            }
        }

        private boolean accept(char c) {
            skipSpaces();
            if (!atEnd() && peek() == c) {
                pos++;
                return true;
            }
            return false;
        }

        private void expect(char c) {
            if (!accept(c)) {
                throw error("expected '" + c + "'");
            }
        }

        void skipSpaces() {
            while (!atEnd() && Character.isWhitespace(peek())) {
                pos++;
            }
        }

        boolean atEnd() {
            return pos >= text.length();
        }

        char peek() {
            return text.charAt(pos);
        }

        IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at position " + (pos + 1));
        }
    }
}
