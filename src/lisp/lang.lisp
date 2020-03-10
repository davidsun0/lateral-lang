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

(list (gensym) (gensym))

(if :test
  (list 1 2)
  '())

(defun cons (a b)
  (prep a b))

(cons 1 (cons 2 '()))
