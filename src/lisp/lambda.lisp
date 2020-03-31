;; Language Essentials

(def t (asm-quote (:getstatic "java/lang/Boolean" "TRUE" "Ljava/lang/Boolean;")))
(def null (asm-quote :aconst_null))

(defun list (:rest lst)
  lst)

(defun gensym
   ()       (gensym "gensym")
   (prefix) (asm-quote (asm-unquote prefix)
                       (:checkcast "java/lang/String")
                       (:invokestatic "io/github/whetfire/lateral/Symbol"
                                      "gensym"
                                      "(Ljava/lang/String;)Lio/github/whetfire/lateral/Symbol;")))

(gensym "abc")
(gensym)

(defun concat (:rest lst)
  (asm-quote (asm-unquote lst)
             (:checkcast "io/github/whetfire/lateral/Sequence")
             (:invokestatic "io/github/whetfire/lateral/Sequence"
                            "concat"
                            "(Lio/github/whetfire/lateral/Sequence;)Lio/github/whetfire/lateral/Sequence;")))

;; Core Language Functions

(defun print (x)
  (asm-quote (asm-unquote x)
             :dup
             (:getstatic "java/lang/System" "out" "Ljava/io/PrintStream;")
             :swap
             (:invokevirtual "java/io/PrintStream" "println" "(Ljava/lang/Object;)V")))

(defun + (a b)
  (asm-quote (asm-unquote a)
             (:checkcast "java/lang/Integer")
             (:invokevirtual "java/lang/Integer" "intValue" "()I")
             (asm-unquote b)
             (:checkcast "java/lang/Integer")
             (:invokevirtual "java/lang/Integer" "intValue" "()I")
             :iadd
             (:invokestatic "java/lang/Integer" "valueOf" "(I)Ljava/lang/Integer;")))

(defun > (a b)
  (asm-quote (asm-unquote a)
             (:checkcast "java/lang/Integer")
             (:invokevirtual "java/lang/Integer" "intValue" "()I")
             (asm-unquote b)
             (:checkcast "java/lang/Integer")
             (:invokevirtual "java/lang/Integer" "intValue" "()I")
             (:if_icmple falsebranch)
             (asm-unquote t)
             :areturn
             (:label falsebranch)
             (asm-unquote null)
             :areturn))

; Playground

(defun adder (n)
  (function (x) (+ x n)))

((adder 100) 321)

(defun inc (n)
  (asm-quote (asm-unquote n)
             (:checkcast "java/lang/Integer")
             (:invokevirtual "java/lang/Integer" "intValue" "()I")
             (:iconst 1)
             :iadd
             (:invokestatic "java/lang/Integer" "valueOf" "(I)Ljava/lang/Integer;")))

(defun dec (n)
  (asm-quote (asm-unquote n)
             (:checkcast "java/lang/Integer")
             (:invokevirtual "java/lang/Integer" "intValue" "()I")
             (:iconst -1)
             :iadd
             (:invokestatic "java/lang/Integer" "valueOf" "(I)Ljava/lang/Integer;")))

(inc (dec 0))

(defun test (n)
  (if (> 0 (print n))
    n
    (recur (dec n))))

(test 10)

(defun my-list (:rest lst)
  lst)

(my-list 1 "two" 'three)
