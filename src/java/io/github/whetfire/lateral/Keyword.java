package io.github.whetfire.lateral;

public final class Keyword {
    private final String name;

    private Keyword(String name) {
        this.name = name;
    }

    public static Keyword makeKeyword(String name) {
        return new Keyword(name);
    }

    public String getValue() {
        return name;
    }

    public int hashCode() {
        return name.hashCode();
    }

    public boolean equals(Object obj) {
        if(obj == this) {
            return true;
        } else {
            return obj instanceof Keyword && name.equals(((Keyword) obj).name);
        }
    }

    public String toString() {
        return ":" + name;
    }
}
