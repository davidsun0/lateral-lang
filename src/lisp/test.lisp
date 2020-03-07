(def LinkedList "io/github/whetfire/lateral/LinkedList")

LinkedList

(defun gensym ()
  (asm (:invokestatic "io/github/whetfire/lateral/Symbol"
                      "gensym"
                      "()Lio/github/whetfire/lateral/Symbol;")))

(gensym)

(if :test
  1
  2)

;    `test

;(defmacro while (condition body)
;  (let (looplab (gensym))
;    `(asm (:goto ,looplab)
;          (de-asm ,body)
;          (:label ,looplab)
;          (de-asm ,condition)
;          :aconst_null)))

'asdf

'()
()

null

;(while 1 2)

(defmacro prep (a b)
  (let (LinkedList     "io/github/whetfire/lateral/LinkedList"
        Sequence       "io/github/whetfire/lateral/Sequence"
        LinkedListType "Lio/github/whetfire/lateral/LinkedList;"
        ObjectType     "Ljava/lang/Object;")
    (list (quote asm)
          (list :new LinkedList)
          :dup
          (list (quote de-asm) a)
          (list (quote de-asm) b)
          (list :checkcast Sequence)
          (list :invokespecial LinkedList "<init>"
                "(Ljava/lang/Object;Lio/github/whetfire/lateral/Sequence;)V"))))

(defun cons (a b)
  (prep a b))

cons

(cons 1 '())



