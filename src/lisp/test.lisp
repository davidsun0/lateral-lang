(defun list (:rest lst)
  lst)

(defun concat (:rest lst)
  (asm (de-asm lst)
       (:invokestatic "io/github/whetfire/lateral/Sequence"
                      "concat"
                      "(Lio/github/whetfire/lateral/Sequence;)Lio/github/whetfire/lateral/Sequence;")))

(list 1 2 3)

(defmacro test (a b)
  `(,a ,b))

(test list ())
