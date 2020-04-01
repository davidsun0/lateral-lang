;; Language Essentials

(def t (asm-quote (:getstatic "java/lang/Boolean" "TRUE" "Ljava/lang/Boolean;")))
(def nil (asm-quote :aconst_null))

;; list is built in to the compiler; this definition is for higher order programming
(defun list (:rest lst)
  lst)

(defun gensym
   ()       (gensym "gensym")
   (prefix) (asm-quote (asm-unquote prefix)
                       (:checkcast "java/lang/String")
                       (:invokestatic "io/github/whetfire/lateral/Symbol"
                                      "gensym"
                                      "(Ljava/lang/String;)Lio/github/whetfire/lateral/Symbol;")))

(defun concat (:rest lst)
  (asm-quote (asm-unquote lst)
             (:checkcast "io/github/whetfire/lateral/Sequence")
             (:invokestatic "io/github/whetfire/lateral/Sequence"
                            "concat"
                            "(Lio/github/whetfire/lateral/Sequence;)Lio/github/whetfire/lateral/Sequence;")))

(defun empty? (x)
  (asm-quote (asm-unquote x)
             (:instanceof "io/github/whetfire/lateral/Sequence")
             (:ifeq falsebranch)
             (asm-unquote x)
             (:checkcast "io/github/whetfire/lateral/Sequence")
             (:invokevirtual "io/github/whetfire/lateral/Sequence"
                             "isEmpty"
                             "()Z")
             (:ifeq falsebranch)
             (asm-unquote t)
             :areturn
             (:label falsebranch)
             (asm-unquote nil)
             :areturn))

(defun prep (x lst)
  (asm-quote (asm-unquote x)
             (asm-unquote lst)
             (:checkcast "io/github/whetfire/lateral/Sequence")
             (:invokestatic "io/github/whetfire/lateral/Sequence"
                            "cons"
                            "(Ljava/lang/Object;Lio/github/whetfire/lateral/Sequence;)Lio/github/whetfire/lateral/Sequence;")))

(defun first (lst)
  (asm-quote (asm-unquote lst)
             (:checkcast "io/github/whetfire/lateral/Sequence")
             (:invokevirtual "io/github/whetfire/lateral/Sequence"
                             "first"
                             "()Ljava/lang/Object;")))

(defun rest (lst)
  (asm-quote (asm-unquote lst)
             (:checkcast "io/github/whetfire/lateral/Sequence")
             (:invokevirtual "io/github/whetfire/lateral/Sequence"
                             "rest"
                             "()Lio/github/whetfire/lateral/Sequence;")))

(defun reverse
  (lst)
  (reverse lst '())

  (lst acc)
  (if (empty? lst)
    acc
    (reverse (rest lst) (prep (first lst) acc))))

(defun or0
  (lst)
  (if (empty? lst)
    'nil
    (let (rev (reverse lst))
      (or0 (rest rev) (first rev))))

  (lst acc)
  (if (empty? lst)
    acc
    (recur (rest lst)
           (list 'if (first lst) 't acc))))

(defun and0
  (lst)
  (if (empty? lst)
    't
    (let (rev (reverse lst))
      (and0 (rest rev) (first rev))))

  (lst acc)
  (if (empty? lst)
    acc
    (recur (rest lst)
           (list 'if (first lst) acc 'nil))))

(defmacro or (:rest lst)
  (or0 lst))

(defmacro and (:rest lst)
  (and0 lst))

(or0 '(a b c))
(and0 '(e f g))

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
             (asm-unquote nil)
             :areturn))

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

(defun map
  (f lst)
  (reverse (map f lst '()))

  (f lst acc)
  (if (empty? lst)
    acc
    (recur f (rest lst) (prep (f (first lst)) acc))))

(defun filter
  (f lst)
  (reverse (filter f lst '()))

  (f lst acc)
  (if (empty? lst)
    acc
    (recur f
           (rest lst)
           (if (f (first lst))
             (prep (first lst) acc)
             acc))))

; Playground

(defun adder (n)
  (function (x) (+ x n)))

((adder 100) 321)

(prep 1 '())
(prep 1 (list 2 3 4))

(reverse (list 5 4 3 2 1))
(def temp (adder 100))
(map inc (list 1 10 1000 2))

(filter (function (x) (> x 0)) (list 1 -3 100 -20 10))

(defun test (x :rest y)
  (if (empty? x)
    (print y)
    (recur '())))

(print :hello)
