(def LinkedList "io/github/whetfire/lateral/LinkedList")

LinkedList

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

(concat (list 1 2) (list 3 4))

(gensym)

`test

;`(a b ,c ,@(d e f) g)

(let (x 0)
  ``,,x)

(defmacro while (condition body)
  (let (looplab (gensym))
    `(asm (:goto ,looplab)
          (de-asm ,body)
          (:label ,looplab)
          (de-asm ,condition)
          :aconst_null)))

(defmacro asm-if (condition then else)
  (let (elselab (gensym)
        endlab  (gensym))
    `(asm (de-asm ,condition)
          (:ifnull ,elselab)
          (de-asm ,then)
          (:goto ,endlab)
          (:label ,elselab)
          (de-asm ,else)
          (:label ,endlab))))

(asm-if null
  (list 1 2)
  (list 3 4))

(defmacro prep (a b)
  `(asm (de-asm ,b)
        (:checkcast "io/github/whetfire/lateral/Sequence")
        (de-asm ,a)
        (:invokevirtual "io/github/whetfire/lateral/Sequence"
                        "cons"
                        "(Ljava/lang/Object;)Lio/github/whetfire/lateral/Sequence;")))

(defun cons (a b)
  (prep a b))

(list cons gensym)

(cons 2 (cons 1 '()))



