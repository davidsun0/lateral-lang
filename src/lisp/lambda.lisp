(def gensym (function ()
  (asm-quote (:invokestatic "io/github/whetfire/lateral/Symbol"
                            "gensym"
                            "()Lio/github/whetfire/lateral/Symbol;"))))

(gensym)
(gensym)
(gensym)

(def print
     (function (x)
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

(print 123)

(def + (function (a b)
  (asm-quote (unquote a)
             (:checkcast "java/lang/Integer")
             (:invokevirtual "java/lang/Integer" "intValue" "()I")
             (unquote b)
             (:checkcast "java/lang/Integer")
             (:invokevirtual "java/lang/Integer" "intValue" "()I")
             :iadd
             (:invokestatic "java/lang/Integer" "valueOf" "(I)Ljava/lang/Integer;")
             :areturn)))

(defun add2 (n)
  (inc (inc n)))

;(add2 40)

(defun inc (n)
  (+ n 1))

(inc 999)

;(add2 40)

(def addn (function (n) (function (x) (+ x n))))

((function (x)
   (let (x 100)
     (print x))) 123)

(defmacro abc (x)
  765)

(defun my-list (:rest args)
  args)

(my-list 1 2 3)
