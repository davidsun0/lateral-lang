(defun gensym ()
  (asm-quote (:invokestatic "io/github/whetfire/lateral/Symbol"
                            "gensym"
                            "()Lio/github/whetfire/lateral/Symbol;")))

(defun concat (:rest lst)
  (asm-quote ,lst
       (:checkcast "io/github/whetfire/lateral/Sequence")
       (:invokestatic "io/github/whetfire/lateral/Sequence"
                      "concat"
                      "(Lio/github/whetfire/lateral/Sequence;)Lio/github/whetfire/lateral/Sequence;")))

(defmacro if (test then else)
  (let (elselab (gensym)
        endlab  (gensym))
    `(asm-quote ,,test
          (:ifnull ,elselab)
          ,,then
          (:goto   ,endlab)
          (:label  ,elselab)
          ,,else
          (:label  ,endlab))))

(defmacro prep (a b)
  `(asm-quote ('unquote ,b)
        (:checkcast "io/github/whetfire/lateral/Sequence")
        ('unquote ,a)
        (:invokevirtual "io/github/whetfire/lateral/Sequence"
                        "cons"
                        "(Ljava/lang/Object;)Lio/github/whetfire/lateral/Sequence;")))

(defun cons (a b)
  (prep a b))

(defmacro invoke-cons ()
  `(asm-quote (:invokevirtual "io/github/whetfire/lateral/Sequence"
                  "cons"
                  "(Ljava/lang/Object;)Lio/github/whetfire/lateral/Sequence;")))

(defmacro prim-int (int-obj)
  `(asm-quote ('unquote ,int-obj)
        (:checkcast "java/lang/Integer")
        (:invokevirtual "java/lang/Integer" "intValue" "()I")))

(defun dec (n)
  (asm-quote ,(prim-int n)
       (:iconst 1)
       :isub
       (:invokestatic "java/lang/Integer" "valueOf" "(I)Ljava/lang/Integer;")))

(defun inc (n)
  (asm-quote ,(prim-int n)
       (:iconst 1)
       :iadd
       (:invokestatic "java/lang/Integer" "valueOf" "(I)Ljava/lang/Integer;")))

(inc 1234)
(dec 1234)

(cons "abc" '())

(define LinkedList "io/github/whetfire/lateral/LinkedList")
LinkedList
