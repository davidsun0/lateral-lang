(defun list (:rest lst)
  lst)

(defun gensym ()
  (asm (:invokestatic "io/github/whetfire/lateral/Symbol"
                      "gensym"
                      "()Lio/github/whetfire/lateral/Symbol;")))

(defun concat (:rest lst)
  (asm (de-asm lst)
       (:checkcast "io/github/whetfire/lateral/Sequence")
       (:invokestatic "io/github/whetfire/lateral/Sequence"
                      "concat"
                      "(Lio/github/whetfire/lateral/Sequence;)Lio/github/whetfire/lateral/Sequence;")))

(defmacro if (test then else)
  (let (elselab (gensym)
        endlab  (gensym))
    `(asm (de-asm  ,test)
          (:ifnull ,elselab)
          (de-asm  ,then)
          (:goto   ,endlab)
          (:label  ,elselab)
          (de-asm  ,else)
          (:label  ,endlab))))

(defmacro prep (a b)
  `(asm (de-asm ,b)
        (:checkcast "io/github/whetfire/lateral/Sequence")
        (de-asm ,a)
        (:invokevirtual "io/github/whetfire/lateral/Sequence"
                        "cons"
                        "(Ljava/lang/Object;)Lio/github/whetfire/lateral/Sequence;")))

(defun cons (a b)
  (prep a b))

(defmacro invoke-cons ()
  `(asm (:invokevirtual "io/github/whetfire/lateral/Sequence"
                  "cons"
                  "(Ljava/lang/Object;)Lio/github/whetfire/lateral/Sequence;")))

(defmacro prim-int (int-obj)
  `(asm (de-asm ,int-obj)
        (:checkcast "java/lang/Integer")
        (:invokevirtual "java/lang/Integer" "intValue" "()I")))

(defun dec (n)
  (asm (de-asm (prim-int n))
       (:iconst 1)
       :isub
       (:invokestatic "java/lang/Integer" "valueOf" "(I)Ljava/lang/Integer;")))

(defun inc (n)
  (asm (de-asm (prim-int n))
       (:iconst 1)
       :iadd
       (:invokestatic "java/lang/Integer" "valueOf" "(I)Ljava/lang/Integer;")))

(inc 123)
(dec 123)

(defun range (low hi)
  (asm (de-asm '())
       (de-asm hi)
       (:checkcast "java/lang/Integer")
       (:goto test)
       (:label loop)
       (de-asm (dec)) ; stack arg hack
       (:checkcast "java/lang/Integer")
       :dup_x1
       (de-asm (invoke-cons))
       :swap
       (:label test)
       :dup
       (de-asm low)
       (:checkcast "java/lang/Integer")
       (:invokevirtual "java/lang/Integer" "compareTo" "(Ljava/lang/Integer;)I")
       (:ifgt loop)
       :pop))

(range 0 10)

