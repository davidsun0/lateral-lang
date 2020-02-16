package io.github.whetfire.lateral;

public final class Symbol {
    public final static Symbol NIL_SYMBOL = makeSymbol("nil");
    public final static Symbol TRUE_SYMBOL = makeSymbol("t");

    private final String value;

    private Symbol(String value){
        this.value = value;
    }

    public static Symbol makeSymbol(String value) {
        return new Symbol(value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if(obj == this)
            return true;
        else
            return obj instanceof Symbol && value.equals(((Symbol) obj).value);
    }

    @Override
    public String toString() {
        return value;
    }
}