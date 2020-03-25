(def gensym (lambda ()
  (asm-quote (:invokestatic "io/github/whetfire/lateral/Symbol"
                            "gensym"
                            "()Lio/github/whetfire/lateral/Symbol;"))))

(gensym)
(gensym)
(gensym)
gensym

(def print
     (lambda (x)
       (asm-quote (:aload 1)
                  :dup
                  (:getstatic "java/lang/System"
                              "out"
                              "Ljava/io/PrintStream;")
                  :swap
                  (:invokevirtual "java/io/PrintStream"
                                  "println"
                                  "(Ljava/lang/Object;)V")
                  :areturn)))

print
(print 123)

(def + (lambda (a b)
  (asm-quote (unquote a)
             (:checkcast "java/lang/Integer")
             (:invokevirtual "java/lang/Integer" "intValue" "()I")
             (unquote b)
             (:checkcast "java/lang/Integer")
             (:invokevirtual "java/lang/Integer" "intValue" "()I")
             :iadd
             (:invokestatic "java/lang/Integer" "valueOf" "(I)Ljava/lang/Integer;")
             :areturn)))

(+ 1 2)

(def inc (lambda (n) (+ n 1)))

(inc 999)

(def addn (lambda (n) (lambda (x) (+ x n))))

(def add5 (addn 5))

(add5 10)

((lambda (x)
   (let (x 100)
     (print x))) 123)
