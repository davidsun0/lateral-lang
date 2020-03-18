package io.github.whetfire.lateral;

public final class Keyword {
    private final String name;
    private final int hash;

    private Keyword(String name) {
        this.name = name;
        hash = name.hashCode();
    }

    public static Keyword makeKeyword(String name) {
        // TODO: Keyword interning
        return new Keyword(name);
    }

    public int hashCode() {
        return hash;
    }

    public boolean equals(Object obj) {
        if(obj == this) {
            return true;
        } else {
            return obj instanceof Keyword && hash == ((Keyword) obj).hash;
        }
    }

    public String getValue() {
        return name;
    }

    public String toString() {
        return ":" + name;
    }
}
